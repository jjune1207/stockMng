package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OHLCV 캔들 통합 DTO (KIS / 네이버 공통)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleDto {

    /** 날짜 (yyyyMMdd 형식) */
    private String date;

    /** 시가 */
    private double open;

    /** 고가 */
    private double high;

    /** 저가 */
    private double low;

    /** 종가 */
    private double close;

    /** 거래량 */
    private long volume;
}
