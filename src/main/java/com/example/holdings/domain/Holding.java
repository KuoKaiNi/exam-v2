package com.example.holdings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false)
    private Integer lots;

    @Column(name = "buy_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyPrice;

    protected Holding() {
    }

    public Holding(String code, Integer lots, BigDecimal buyPrice) {
        this.code = code;
        this.lots = lots;
        this.buyPrice = buyPrice;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
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
