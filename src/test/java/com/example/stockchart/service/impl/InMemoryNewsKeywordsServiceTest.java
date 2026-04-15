package com.example.stockchart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryNewsKeywordsServiceTest {

    @TempDir
    Path tempDir;

    private InMemoryNewsKeywordsService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryNewsKeywordsService(new ObjectMapper(), tempDir);
        service.loadFromFile();
    }

    @Test
    void 파일이_없으면_기본_키워드로_초기화한다() {
        List<String> keywords = service.getKeywords();

        assertThat(keywords).isNotEmpty();
        assertThat(keywords).contains("미국", "나스닥", "Fed");
        assertThat(Files.exists(tempDir.resolve("news-keywords.json"))).isTrue();
    }

    @Test
    void 키워드를_중복_제거하고_저장한다() throws Exception {
        List<String> saved = service.updateKeywords(List.of(" 나스닥 ", "Fed", "나스닥", "", "S&P"));

        assertThat(saved).containsExactly("나스닥", "Fed", "S&P");
        assertThat(service.getKeywords()).containsExactly("나스닥", "Fed", "S&P");
        assertThat(Files.readString(tempDir.resolve("news-keywords.json"))).contains("나스닥");
    }
}
