package com.example.stockchart.service;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;

import java.util.List;

/**
 * 네이버 증권 API 기반 주식/ETF 데이터 서비스
 */
public interface NaverStockService {

    /**
     * 일봉 캔들 데이터 조회 (MA200 계산을 위해 충분한 데이터 반환)
     *
     * @param symbol 종목/ETF 코드
     * @return OHLCV 캔들 리스트 (날짜 오름차순)
     */
    List<CandleDto> getDailyCandles(String symbol);

    /**
     * 현재가 조회 (네이버 모바일 API)
     *
     * @param symbol 종목/ETF 코드
     * @return 현재가 DTO
     */
    StockPriceDto getCurrentPrice(String symbol);

    /**
     * 종목/ETF 검색 (네이버 자동완성 API)
     *
     * @param keyword 검색어 (종목명 또는 코드)
     * @return 검색 결과 리스트
     */
    List<StockSearchDto> searchStock(String keyword);

    /**
     * 당일 거래량 상위 종목/ETF 목록 조회
     *
     * @param type stock 또는 etf
     * @param limit 최대 건수
     * @return 거래량 상위 목록
     */
    List<StockSearchDto> getTopByVolume(String type, int limit);
}
