package com.example.stockchart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class StockChartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockChartApplication.class, args);
    }
}

