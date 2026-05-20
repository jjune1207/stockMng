package com.example.stockchart.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class OwnerAuthService {

    private static final String DATA_DIR = "data";
    private static final String OWNERS_FILE = "owners.json";
    private static final int MIN_PASSWORD_LENGTH = 4;
    static final String DEFAULT_ADMIN_PASSWORD = "stock1234!";

    @Value("${admin.name:admin}")
    private String adminName;

    @Value("${admin.password:}")
    private String adminPassword;

    private final ObjectMapper objectMapper;
    private final Path filePath;
    private final List<OwnerAccount> accounts = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public OwnerAuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.filePath = Paths.get(DATA_DIR, OWNERS_FILE);
    }

    @PostConstruct
    void load() {
        if (!Files.exists(filePath)) {
            log.info("소유자 계정 파일 없음, 빈 목록으로 시작");
            return;
        }
        try {
            List<OwnerAccount> loaded = objectMapper.readValue(filePath.toFile(), new TypeReference<>() {});
            accounts.addAll(loaded);
            log.info("소유자 계정 {}개 로드", accounts.size());
        } catch (IOException e) {
            log.error("소유자 계정 파일 로드 실패: {}", e.getMessage());
        }
    }

    public boolean isAdminCredentials(String name, String password) {
        if (adminPassword == null || adminPassword.isBlank()) return false;
        return adminName.equals(name) && adminPassword.equals(password);
    }

    public boolean authenticate(String name, String password) {
        String hash = sha256(password);
        return accounts.stream()
            .anyMatch(a -> a.getName().equals(name) && a.getPasswordHash().equals(hash));
    }

    public boolean isMustChangePassword(String name) {
        return accounts.stream()
            .filter(a -> a.getName().equals(name))
            .findFirst()
            .map(OwnerAccount::isMustChangePassword)
            .orElse(false);
    }

    public void changePassword(String name, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상 입력해 주세요.");
        }
        if (newPassword.equals(DEFAULT_ADMIN_PASSWORD)) {
            throw new IllegalArgumentException("초기 비밀번호는 사용할 수 없습니다. 다른 비밀번호를 입력해 주세요.");
        }
        synchronized (lock) {
            accounts.replaceAll(a -> {
                if (a.getName().equals(name)) {
                    return OwnerAccount.builder()
                        .name(a.getName())
                        .passwordHash(sha256(newPassword))
                        .mustChangePassword(false)
                        .build();
                }
                return a;
            });
            persist();
        }
        log.info("비밀번호 변경 완료: {}", name);
    }

    public void adminCreate(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }
        String trimmed = name.trim();
        if (trimmed.equals(adminName)) {
            throw new IllegalArgumentException("사용할 수 없는 이름입니다.");
        }
        if (!trimmed.matches("^[\\w가-힣]{1,20}$")) {
            throw new IllegalArgumentException("이름은 한글/영문/숫자/밑줄 1~20자로 입력해 주세요.");
        }
        if (existsByName(trimmed)) {
            throw new IllegalArgumentException("이미 사용 중인 이름입니다.");
        }
        synchronized (lock) {
            accounts.add(OwnerAccount.builder()
                .name(trimmed)
                .passwordHash(sha256(DEFAULT_ADMIN_PASSWORD))
                .mustChangePassword(true)
                .build());
            persist();
        }
        log.info("관리자가 소유자 계정 생성: {}", trimmed);
    }

    public void register(String name, String password) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }
        String trimmed = name.trim();
        if (trimmed.equals(adminName)) {
            throw new IllegalArgumentException("사용할 수 없는 이름입니다.");
        }
        if (!trimmed.matches("^[\\w가-힣]{1,20}$")) {
            throw new IllegalArgumentException("이름은 한글/영문/숫자/밑줄 1~20자로 입력해 주세요.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상 입력해 주세요.");
        }
        if (existsByName(trimmed)) {
            throw new IllegalArgumentException("이미 사용 중인 이름입니다.");
        }

        synchronized (lock) {
            accounts.add(OwnerAccount.builder()
                .name(trimmed)
                .passwordHash(sha256(password))
                .build());
            persist();
        }
        log.info("신규 소유자 등록: {}", trimmed);
    }

    public boolean existsByName(String name) {
        return accounts.stream().anyMatch(a -> a.getName().equals(name));
    }

    public List<String> getAccountNames() {
        return accounts.stream().map(OwnerAccount::getName).sorted().toList();
    }

    public void deleteAccount(String name) {
        synchronized (lock) {
            accounts.removeIf(a -> a.getName().equals(name));
            persist();
        }
        log.info("소유자 계정 삭제: {}", name);
    }

    private void persist() {
        try {
            Files.createDirectories(filePath.getParent());
            Path tmp = Files.createTempFile(filePath.getParent(), "owners", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), new ArrayList<>(accounts));
                try {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("소유자 계정 저장 실패: " + e.getMessage(), e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
