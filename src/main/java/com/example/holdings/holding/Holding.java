package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private int lots;

    @Column(name = "buy_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal buyPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Holding() {
    }

    public Holding(String code, int lots, BigDecimal buyPrice, Instant now) {
        this.code = code;
        this.lots = lots;
        this.buyPrice = buyPrice;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(int lots, BigDecimal buyPrice, Instant now) {
        this.lots = lots;
        this.buyPrice = buyPrice;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public int getLots() { return lots; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
