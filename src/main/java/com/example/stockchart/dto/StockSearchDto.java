package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 종목 검색 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchDto {

    /** 종목 코드 */
    private String symbol;

    /** 종목명 */
    private String name;

    /** 시장 구분 (KOSPI / KOSDAQ) */
    private String market;

    /** 종목 유형 (stock / etf) */
    @Builder.Default
    private String type = "stock";

    /** 한글 설명 (미국 ETF 전용, optional) */
    private String description;
}
