package com.example.stockchart.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Thymeleaf 페이지 라우팅 컨트롤러
 */
@Controller
public class ChartViewController {

    /** 메인 페이지 (종목 검색 + 주요 종목 현재가 테이블) */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "주식 분석 차트");
        return "index";
    }

    /** 종목 차트 페이지 */
    @GetMapping("/chart/{symbol}")
    public String chart(@PathVariable("symbol") String symbol, Model model) {
        model.addAttribute("symbol", symbol);
        model.addAttribute("pageTitle", symbol + " 차트");
        return "chart";
    }

    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
