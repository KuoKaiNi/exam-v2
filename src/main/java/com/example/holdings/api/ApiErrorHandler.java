package com.example.holdings.api;

import com.example.holdings.domain.HoldingException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** API 錯誤一律是 {"error":{"code":"...","message":"..."}}，App 會直接顯示 message。 */
@RestControllerAdvice(basePackages = "com.example.holdings.api")
public class ApiErrorHandler {

    @ExceptionHandler(HoldingException.class)
    public ResponseEntity<Map<String, Object>> holdingError(HoldingException e) {
        return error(e.getStatus(), e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "請求內容不是合法的 JSON");
    }

    /** 路徑的 id 不是數字，等同查無此筆。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> badPath(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到這筆持股");
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
