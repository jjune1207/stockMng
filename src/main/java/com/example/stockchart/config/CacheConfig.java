package com.example.stockchart.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${cache.candle-ttl-minutes:10}")
    private int candleTtlMinutes;

    @Value("${cache.price-ttl-seconds:30}")
    private int priceTtlSeconds;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 현재가 캐시: 30초 TTL
        cacheManager.registerCustomCache("stockPrice",
            Caffeine.newBuilder()
                .expireAfterWrite(priceTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(100)
                .build());

        // 캔들 데이터 캐시: 10분 TTL
        cacheManager.registerCustomCache("stockCandle",
            Caffeine.newBuilder()
                .expireAfterWrite(candleTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(200)
                .build());

        // 분봉 캔들 데이터 캐시: 1분 TTL (장중 실시간성)
        cacheManager.registerCustomCache("stockMinuteCandle",
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build());

        // 종목 검색 캐시: 60분 TTL
        cacheManager.registerCustomCache("stockSearch",
            Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(500)
                .build());

        // 거래량 상위 랭킹 캐시: 10분 TTL (프론트엔드 갱신 주기와 동일)
        cacheManager.registerCustomCache("topRanking",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(20)
                .build());

        // 시장 지표 캐시: 5분 TTL (코스피/코스닥/환율/WTI/해외지수)
        cacheManager.registerCustomCache("marketIndicators",
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(5)
                .build());

        return cacheManager;
    }
}
