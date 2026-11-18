package com.example.holdings.domain;

import org.springframework.http.HttpStatus;

/** 業務錯誤。status 與 errorCode 對應 API 規格；網頁版只取 message 顯示。 */
public class HoldingException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public HoldingException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
