package com.example.stockchart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UsNewsDto {
    private String title;
    private String link;
    private String pubDate;
    private String description;
    private String source;
}
