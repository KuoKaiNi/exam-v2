package com.example.holdings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.example.holdings.quote.Market;
import java.math.BigDecimal;

@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(nullable = false)
    private Integer lots;

    @Column(name = "buy_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyPrice;

    protected Holding() {
    }

    public Holding(String code, String name, Market market, Integer lots, BigDecimal buyPrice) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.lots = lots;
        this.buyPrice = buyPrice;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Market getMarket() {
        return market;
    }

    public Integer getLots() {
        return lots;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void changeLotsAndPrice(Integer lots, BigDecimal buyPrice) {
        this.lots = lots;
        this.buyPrice = buyPrice;
    }
}
