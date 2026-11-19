package com.example.holdings.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_quote")
public class StockQuote {

    @Id
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String name;

    @Column(name = "close_price", precision = 10, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "price_date")
    private LocalDate priceDate;

    /** 最後一次成功向官方查詢的時間。 */
    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error")
    private String lastError;

    protected StockQuote() {
    }

    public StockQuote(String code, Market market, String name) {
        this.code = code;
        this.market = market;
        this.name = name;
    }

    /** 記下這次查到的結果。latest 為 null 表示官方這段期間沒有成交資料，保留舊的收盤價。 */
    public void recordSuccess(String newName, DailyClose latest, Instant now) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
        if (latest != null && (priceDate == null || !latest.date().isBefore(priceDate))) {
            this.closePrice = latest.close();
            this.priceDate = latest.date();
        }
        this.checkedAt = now;
        this.lastError = null;
        this.lastErrorAt = null;
    }

    public void recordError(String message, Instant now) {
        this.lastErrorAt = now;
        this.lastError = message == null ? null : message.substring(0, Math.min(300, message.length()));
    }

    public String getCode() { return code; }
    public Market getMarket() { return market; }
    public String getName() { return name; }
    public BigDecimal getClosePrice() { return closePrice; }
    public LocalDate getPriceDate() { return priceDate; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }
}
