package com.example.stockchart.exception;

/**
 * 주식 API 호출 실패 시 발생하는 커스텀 예외
 */
public class StockApiException extends RuntimeException {

    private final int statusCode;

    public StockApiException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public StockApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public StockApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public StockApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
