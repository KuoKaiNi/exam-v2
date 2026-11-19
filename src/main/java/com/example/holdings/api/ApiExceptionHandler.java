package com.example.holdings.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.holdings.holding.HoldingException;

/** API 的錯誤一律回 {"error":{"code","message"}}，App 才能顯示 message；不能讓 Spring 預設的錯誤頁跑出來。 */
@RestControllerAdvice(assignableTypes = {HoldingApiController.class, ApiFallbackController.class})
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final MediaType JSON_UTF8 = MediaType.parseMediaType(HoldingApiController.JSON_UTF8);

    @ExceptionHandler(HoldingException.class)
    public ResponseEntity<ApiError> holding(HoldingException e) {
        HttpStatus status = switch (e.getKind()) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND, STOCK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_CODE -> HttpStatus.CONFLICT;
            case QUOTE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return error(status, e.getKind().name(), e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "不支援 " + e.getMethod() + " 方法");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "請求內容要是 JSON");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("API 發生未預期的錯誤", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "伺服器發生錯誤，請稍後再試");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(JSON_UTF8).body(ApiError.of(code, message));
    }
}
