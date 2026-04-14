package com.example.stockchart.service.impl;

import com.example.stockchart.service.NewsKeywordsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;
    private final Path filePath;

    public InMemoryNewsKeywordsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.filePath = Paths.get(DATA_DIR, KEYWORDS_FILE);
    }

    @PostConstruct
    void loadFromFile() {
        if (!Files.exists(filePath)) {
            keywords.addAll(DEFAULT_KEYWORDS);
            saveToFile();
            log.info("뉴스 키워드 파일 없음, 기본값으로 초기화: {}", filePath.toAbsolutePath());
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            List<String> loaded = objectMapper.readValue(bytes, new TypeReference<>() {});
            keywords.addAll(loaded);
            log.info("뉴스 키워드 {}개 로드 완료: {}", keywords.size(), filePath.toAbsolutePath());
        } catch (IOException e) {
            keywords.addAll(DEFAULT_KEYWORDS);
            log.error("뉴스 키워드 파일 로드 실패, 기본값 사용: {}", e.getMessage());
        }
    }

    @Override
    public List<String> getKeywords() {
        return new ArrayList<>(keywords);
    }

    @Override
    public List<String> updateKeywords(List<String> newKeywords) {
        keywords.clear();
        if (newKeywords != null) {
            newKeywords.stream()
                .map(String::trim)
                .filter(k -> !k.isBlank())
                .forEach(keywords::add);
        }
        saveToFile();
        log.info("뉴스 키워드 업데이트: {}개", keywords.size());
        return new ArrayList<>(keywords);
    }

    private void saveToFile() {
        try {
            Files.createDirectories(filePath.getParent());
            objectMapper.writeValue(filePath.toFile(), new ArrayList<>(keywords));
        } catch (IOException e) {
            log.error("뉴스 키워드 파일 저장 실패: {}", e.getMessage());
        }
    }
}
