package com.example.stockchart.service;

import java.util.List;

/**
 * 뉴스 키워드 관리 서비스
 */
public interface NewsKeywordsService {

    /**
     * 현재 뉴스 필터 키워드 목록 조회
     */
    List<String> getKeywords();

    /**
     * 뉴스 필터 키워드 목록 교체 저장
     *
     * @param keywords 새로운 키워드 목록
     * @return 저장된 키워드 목록
     */
    List<String> updateKeywords(List<String> keywords);
}
