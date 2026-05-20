package com.example.stockchart.service.impl;

import com.example.stockchart.service.NewsKeywordsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InMemoryNewsKeywordsService implements NewsKeywordsService {

    private static final String DATA_DIR = "data";
    private static final String KEYWORDS_FILE = "news-keywords.json";

    static final List<String> DEFAULT_KEYWORDS = List.of(
        "미국", "나스닥", "S&P", "S&P500", "다우", "뉴욕", "월가", "Fed", "연준", "NYSE", "NASDAQ", "트럼프"
    );

    /** key: 소유자명, value: 키워드 목록 */
    private final Map<String, List<String>> ownerKeywords = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final ObjectMapper objectMapper;
    private final Path filePath;

    @Autowired
    public InMemoryNewsKeywordsService(ObjectMapper objectMapper) {
        this(objectMapper, Paths.get(DATA_DIR));
    }

    InMemoryNewsKeywordsService(ObjectMapper objectMapper, Path dataDirectory) {
        this.objectMapper = objectMapper;
        this.filePath = dataDirectory.resolve(KEYWORDS_FILE);
    }

    @PostConstruct
    void loadFromFile() {
        synchronized (lock) {
            ownerKeywords.clear();
            if (!Files.exists(filePath)) {
                log.info("뉴스 키워드 파일 없음, 빈 상태로 시작: {}", filePath.toAbsolutePath());
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                Object parsed = objectMapper.readValue(bytes, Object.class);
                if (parsed instanceof List) {
                    // 구형 배열 포맷 → 마이그레이션: "__global__" 키로 임시 보존하지 않고 버림
                    // (소유자별로 분리된 이후에는 전역 키워드를 각 소유자에게 강제 배정할 수 없으므로 무시)
                    log.info("구형 전역 키워드 포맷 감지 — 소유자별 포맷으로 마이그레이션 (기존 값 무시)");
                } else {
                    Map<String, List<String>> loaded = objectMapper.readValue(bytes, new TypeReference<>() {});
                    loaded.forEach((owner, kws) -> {
                        List<String> normalized = normalizeList(kws);
                        if (!normalized.isEmpty()) {
                            ownerKeywords.put(owner, normalized);
                        }
                    });
                    log.info("뉴스 키워드 소유자 {}명 로드 완료", ownerKeywords.size());
                }
            } catch (IOException e) {
                log.error("뉴스 키워드 파일 로드 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<String> getKeywords(String owner) {
        synchronized (lock) {
            List<String> kws = ownerKeywords.get(owner);
            return kws != null ? new ArrayList<>(kws) : new ArrayList<>(DEFAULT_KEYWORDS);
        }
    }

    @Override
    public List<String> updateKeywords(String owner, List<String> newKeywords) {
        synchronized (lock) {
            List<String> normalized = normalizeList(newKeywords);
            ownerKeywords.put(owner, normalized);
            persist();
            log.info("뉴스 키워드 업데이트: owner={}, {}개", owner, normalized.size());
            return new ArrayList<>(normalized);
        }
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        raw.stream()
            .map(k -> k == null ? "" : k.trim())
            .filter(k -> !k.isBlank())
            .forEach(set::add);
        return new ArrayList<>(set);
    }

    private void persist() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "news-keywords", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(tmp.toFile(), new TreeMap<>(ownerKeywords));
                try {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("뉴스 키워드 저장 실패: " + e.getMessage(), e);
        }
    }
}
