package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemDto {

    private String symbol;
    private String name;
    private String market;

    @Builder.Default
    private String type = "stock";

    private String group;

    /** 보유수량 */
    private Double quantity;

    /** 평균단가 (표시 단위: KRW=원, USD=달러) */
    private Double purchasePrice;
}
