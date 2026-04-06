package com.example.stockchart.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistRequestDto {

    private String symbol;
    private String name;
    private String market;
    private String type;
    private String group;
}
