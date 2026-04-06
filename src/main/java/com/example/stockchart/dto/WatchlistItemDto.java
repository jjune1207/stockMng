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
}
