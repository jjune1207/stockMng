package com.example.stockchart.service;

import java.util.List;

public interface NewsKeywordsService {

    List<String> getKeywords(String owner);

    List<String> updateKeywords(String owner, List<String> keywords);
}
