package com.example.holdings.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";

    @Id
    @Column(name = "idem_key")
    private String key;

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false)
    private String status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String fingerprint, Instant now) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.status = PROCESSING;
        this.createdAt = now;
    }

    public void complete(int status, String body) {
        this.status = DONE;
        this.responseStatus = status;
        this.responseBody = body;
    }

    public void restart(Instant now) {
        this.createdAt = now;
    }

    public String getKey() { return key; }
    public String getFingerprint() { return fingerprint; }
    public String getStatus() { return status; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
}
