package com.example.holdings.holding;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 部位：一個股票代號一筆（代號唯一）。股數、成本都由底下的交易（{@link Trade}）算出來。
 * id 沿用 1.0 的持股 id，App 1.0 記住的 id 升級後仍然有效。
 */
@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Holding() {
    }

    public Holding(String code, Instant now) {
        this.code = code;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 底下的交易有變動時呼叫，讓 version 加一（別的裝置拿舊 version 來改會被擋）。 */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
