package com.example.stockchart.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StockApiException.class)
    public ResponseEntity<Map<String, Object>> handleStockApiException(StockApiException e) {
        log.error("주식 API 오류 [{}]: {}", e.getStatusCode(), e.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatusCode());
        body.put("error", e.getMessage());
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청 파라미터: {}", e.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", e.getMessage());
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("서버 내부 오류: {}", e.getMessage(), e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "서버 내부 오류가 발생했습니다.");
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
