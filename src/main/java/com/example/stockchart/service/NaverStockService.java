package com.example.stockchart.service;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.MarketIndicatorDto;
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
     * 분봉 캔들 데이터 조회 (네이버 모바일 API)
     *
     * @param symbol 종목/ETF 코드
     * @param intervalMinutes 분 단위 (1, 3, 10)
     * @return OHLCV 캔들 리스트 (시간 오름차순)
     */
    List<CandleDto> getMinuteCandles(String symbol, int intervalMinutes);

    /**
     * 당일 거래량 상위 종목/ETF 목록 조회
     *
     * @param type stock 또는 etf
     * @param limit 최대 건수
     * @return 거래량 상위 목록
     */
    List<StockSearchDto> getTopByVolume(String type, int limit);

    /**
     * 주요 시장 지표 조회 (코스피, 코스닥, 환율, WTI, S&P 500, 나스닥, 다우지수)
     *
     * @return 시장 지표 리스트 (스파크라인 히스토리 포함)
     */
    List<MarketIndicatorDto> getMarketIndicators();
}
