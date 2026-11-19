package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.example.holdings.pnl.PositionCalculator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 一筆買進或賣出。 */
@Entity
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holding_id", nullable = false, updatable = false)
    private Long holdingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PositionCalculator.Side side;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 1.0 沒有記日期（升級轉入或用 App 1.0 新增），日期是用記錄當天代替的。 */
    @Column(name = "date_estimated", nullable = false)
    private boolean dateEstimated;

    @Column(nullable = false)
    private long shares;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Trade() {
    }

    public Trade(Long holdingId, PositionCalculator.Side side, LocalDate tradeDate, boolean dateEstimated,
                 long shares, BigDecimal price, Instant now) {
        this.holdingId = holdingId;
        this.side = side;
        this.tradeDate = tradeDate;
        this.dateEstimated = dateEstimated;
        this.shares = shares;
        this.price = price;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(LocalDate tradeDate, boolean dateEstimated, long shares, BigDecimal price, Instant now) {
        this.tradeDate = tradeDate;
        this.dateEstimated = dateEstimated;
        this.shares = shares;
        this.price = price;
        this.updatedAt = now;
    }

    /** 給計算用。還沒存檔的新交易 id 是 null，用 Long.MAX_VALUE 排在同一天同方向的最後。 */
    public PositionCalculator.TradeInput toInput() {
        return new PositionCalculator.TradeInput(id == null ? Long.MAX_VALUE : id, side, tradeDate, shares, price);
    }

    public Long getId() { return id; }
    public Long getHoldingId() { return holdingId; }
    public PositionCalculator.Side getSide() { return side; }
    public LocalDate getTradeDate() { return tradeDate; }
    public boolean isDateEstimated() { return dateEstimated; }
    public long getShares() { return shares; }
    public BigDecimal getPrice() { return price; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
