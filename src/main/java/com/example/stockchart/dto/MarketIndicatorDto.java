package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주요 시장 지표 DTO (코스피, 코스닥, 환율, WTI, S&P 500, 나스닥, 다우지수)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndicatorDto {
    /** 지표 식별자 (예: KOSPI, KOSDAQ, USDKRW, WTI, SP500, NASDAQ, DJI) */
    private String id;
    /** 표시 이름 */
    private String name;
    /** 현재 값 */
    private double currentValue;
    /** 전일 대비 변동량 */
    private double change;
    /** 전일 대비 등락률 (%) */
    private double changeRate;
    /** 최근 종가 목록 (스파크라인 차트용, 최대 30개) */
    private List<Double> history;
}
