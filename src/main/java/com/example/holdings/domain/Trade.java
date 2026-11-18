package com.example.holdings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 一筆買進或賣出。amount、fee、tax 在寫入當下算好，之後規則變了歷史也不變。 */
@Entity
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    /** 1.0 升級來的買進沒有日期，為 null。 */
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(nullable = false)
    private Integer shares;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long fee;

    @Column(nullable = false)
    private Long tax;

    protected Trade() {
    }

    public Trade(Long holdingId, Side side, LocalDate tradeDate, int shares, BigDecimal price,
                 long amount, long fee, long tax) {
        this.holdingId = holdingId;
        this.side = side;
        this.tradeDate = tradeDate;
        this.shares = shares;
        this.price = price;
        this.amount = amount;
        this.fee = fee;
        this.tax = tax;
    }

    /** 1.0 的修改：只用在整張、單一買進的持股。 */
    public void changeBuy(int shares, BigDecimal price, long amount, long fee) {
        this.shares = shares;
        this.price = price;
        this.amount = amount;
        this.fee = fee;
    }

    public Long getId() {
        return id;
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public Side getSide() {
        return side;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public int getShares() {
        return shares;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getAmount() {
        return amount;
    }

    public long getFee() {
        return fee;
    }

    public long getTax() {
        return tax;
    }
}
