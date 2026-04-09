package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 현재가 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceDto {

    /** 종목 코드 */
    private String symbol;

    /** 종목명 */
    private String name;

    /** 현재가 */
    private long currentPrice;

    /** 전일 대비 (등락폭) */
    private long priceChange;

    /** 등락률 (%) */
    private double changeRate;

    /** 누적 거래량 */
    private long volume;

    /** 당일 고가 */
    private long high;

    /** 당일 저가 */
    private long low;

    /** 시가 */
    private long open;

    /** 통화 (KRW: 한국, USD: 미국) */
    @Builder.Default
    private String currency = "KRW";
}
