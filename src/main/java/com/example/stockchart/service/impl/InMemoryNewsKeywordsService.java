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
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class InMemoryNewsKeywordsService implements NewsKeywordsService {

    private static final String DATA_DIR = "data";
    private static final String KEYWORDS_FILE = "news-keywords.json";

    private static final List<String> DEFAULT_KEYWORDS = List.of(
        "미국", "나스닥", "S&P", "S&P500", "다우", "뉴욕", "월가", "Fed", "연준", "NYSE", "NASDAQ", "트럼프"
    );

    private final List<String> keywords = new CopyOnWriteArrayList<>();
    private final Object keywordsLock = new Object();
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
        synchronized (keywordsLock) {
            keywords.clear();
            if (!Files.exists(filePath)) {
                keywords.addAll(DEFAULT_KEYWORDS);
                try {
                    persistSnapshotLocked();
                } catch (IllegalStateException e) {
                    log.error("뉴스 키워드 기본값 저장 실패: {}", e.getMessage());
                }
                log.info("뉴스 키워드 파일 없음, 기본값으로 초기화: {}", filePath.toAbsolutePath());
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                List<String> loaded = objectMapper.readValue(bytes, new TypeReference<>() {});
                loaded.stream()
                    .map(this::normalizeKeyword)
                    .filter(k -> !k.isBlank())
                    .distinct()
                    .forEach(keywords::add);
                if (keywords.isEmpty()) {
                    keywords.addAll(DEFAULT_KEYWORDS);
                }
                log.info("뉴스 키워드 {}개 로드 완료: {}", keywords.size(), filePath.toAbsolutePath());
            } catch (IOException e) {
                keywords.clear();
                keywords.addAll(DEFAULT_KEYWORDS);
                log.error("뉴스 키워드 파일 로드 실패, 기본값 사용: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<String> getKeywords() {
        synchronized (keywordsLock) {
            return new ArrayList<>(keywords);
        }
    }

    @Override
    public List<String> updateKeywords(List<String> newKeywords) {
        synchronized (keywordsLock) {
            keywords.clear();
            if (newKeywords != null) {
                LinkedHashSet<String> normalized = new LinkedHashSet<>();
                newKeywords.stream()
                    .map(this::normalizeKeyword)
                    .filter(k -> !k.isBlank())
                    .forEach(normalized::add);
                keywords.addAll(normalized);
            }
            persistSnapshotLocked();
            log.info("뉴스 키워드 업데이트: {}개", keywords.size());
            return new ArrayList<>(keywords);
        }
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private void persistSnapshotLocked() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = Files.createTempFile(parent, "news-keywords", ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), new ArrayList<>(keywords));
                try {
                    Files.move(tempFile, filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveException) {
                    Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("뉴스 키워드 파일 저장 실패: " + e.getMessage(), e);
        }
    }
}
