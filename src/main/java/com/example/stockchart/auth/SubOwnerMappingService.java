package com.example.stockchart.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SubOwnerMappingService {

    private static final String DATA_DIR = "data";
    private static final String MAPPING_FILE = "sub-owner-mapping.json";

    private final ObjectMapper objectMapper;
    private final Path filePath;
    /** key: 서브 소유자명, value: 상위 계정명 */
    private final Map<String, String> mapping = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public SubOwnerMappingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.filePath = Paths.get(DATA_DIR, MAPPING_FILE);
    }

    @PostConstruct
    void load() {
        if (!Files.exists(filePath)) {
            log.info("서브 소유자 매핑 파일 없음, 빈 매핑으로 시작");
            return;
        }
        try {
            Map<String, String> loaded = objectMapper.readValue(filePath.toFile(), new TypeReference<>() {});
            mapping.putAll(loaded);
            log.info("서브 소유자 매핑 {}개 로드", mapping.size());
        } catch (IOException e) {
            log.error("서브 소유자 매핑 파일 로드 실패: {}", e.getMessage());
        }
    }

    public void register(String subOwnerName, String accountName) {
        synchronized (lock) {
            mapping.put(subOwnerName.trim(), accountName.trim());
            persist();
        }
        log.info("서브 소유자 등록: {} → {}", subOwnerName, accountName);
    }

    public void delete(String subOwnerName) {
        synchronized (lock) {
            mapping.remove(subOwnerName);
            persist();
        }
        log.info("서브 소유자 삭제: {}", subOwnerName);
    }

    /** accountName에 속한 서브 소유자 목록 반환 */
    public List<String> getSubOwnersForAccount(String accountName) {
        return mapping.entrySet().stream()
            .filter(e -> accountName.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());
    }

    /** accountNames 목록 기준으로 account → 서브소유자 계층 구조 반환 */
    public Map<String, List<String>> getHierarchyForAccounts(List<String> accountNames) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        accountNames.forEach(account -> result.put(account, getSubOwnersForAccount(account)));
        return result;
    }

    public boolean isSubOwnerOf(String subOwnerName, String accountName) {
        return accountName.equals(mapping.get(subOwnerName));
    }

    public boolean isKnownSubOwner(String ownerName) {
        return mapping.containsKey(ownerName);
    }

    private void persist() {
        try {
            Files.createDirectories(filePath.getParent());
            Path tmp = Files.createTempFile(filePath.getParent(), "sub-owner-mapping", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), new TreeMap<>(mapping));
                try {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("서브 소유자 매핑 저장 실패: " + e.getMessage(), e);
        }
    }
}
