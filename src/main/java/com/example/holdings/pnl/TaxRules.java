package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 證交稅率（只有賣出收）。依代號與「賣出日期」決定。
 * <ul>
 * <li>一般股票 0.3%</li>
 * <li>ETF（代號 00 開頭）0.1%</li>
 * </ul>
 */
public record TaxRules() {

    public static final TaxRules DEFAULT = new TaxRules();

    static final BigDecimal STOCK_RATE = new BigDecimal("0.003");
    static final BigDecimal ETF_RATE = new BigDecimal("0.001");

    public BigDecimal rate(String code, LocalDate sellDate) {
        return isEtf(code) ? ETF_RATE : STOCK_RATE;
    }

    public static boolean isEtf(String code) {
        return code != null && code.startsWith("00");
    }
}
