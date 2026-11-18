package com.example.holdings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key")
public class IdempotencyRecord {

    @Id
    @Column(name = "key_value", length = 100)
    private String keyValue;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "response_body", nullable = false, length = 20000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String keyValue, String fingerprint, int statusCode, String responseBody,
                             LocalDateTime createdAt) {
        this.keyValue = keyValue;
        this.fingerprint = fingerprint;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
