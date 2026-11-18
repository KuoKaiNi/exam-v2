package com.example.holdings.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 手續費與證交稅、盈虧、收益率。金額以元為單位，手續費與稅都無條件捨去到元。
 * 股數是 1 到 999,999 股之間的整數（零股也算），成交金額四捨五入到元。
 */
public final class PnlCalculator {

    public static final int SHARES_PER_LOT = 1000;
    static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    static final BigDecimal MIN_FEE = BigDecimal.valueOf(20);
    static final BigDecimal STOCK_TAX_RATE = new BigDecimal("0.003");
    static final BigDecimal ETF_TAX_RATE = new BigDecimal("0.001");

    /** 1.0 規格的單筆試算結果（整張、單一買進）。 */
    public record Result(long buyCost, long marketValue, long pnl, BigDecimal returnPct) {
    }

    private PnlCalculator() {
    }

    /** 成交金額：價格 × 股數，四捨五入到元。 */
    public static long amount(BigDecimal price, int shares) {
        return price.multiply(BigDecimal.valueOf(shares)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 手續費：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元。 */
    public static long fee(long amount) {
        return BigDecimal.valueOf(amount).multiply(FEE_RATE).setScale(0, RoundingMode.DOWN)
                .max(MIN_FEE).longValueExact();
    }

    /** 代號 00 開頭、B 結尾，視為被動式債券 ETF。 */
    public static boolean isBondEtf(String code) {
        return code.startsWith("00") && code.endsWith("B");
    }

    /**
     * 證交稅率（只在賣出時收）。
     * 一般股票 0.3%；ETF（00 開頭）0.1%；被動式債券 ETF 在免徵期間（含 date 當天）為 0。
     * date 為 null 時不套用免徵。
     */
    public static BigDecimal taxRate(String code, LocalDate date, LocalDate bondEtfExemptUntil) {
        if (isBondEtf(code)) {
            boolean exempt = date != null && bondEtfExemptUntil != null && !date.isAfter(bondEtfExemptUntil);
            return exempt ? BigDecimal.ZERO : ETF_TAX_RATE;
        }
        return code.startsWith("00") ? ETF_TAX_RATE : STOCK_TAX_RATE;
    }

    public static long tax(String code, long amount, LocalDate date, LocalDate bondEtfExemptUntil) {
        return BigDecimal.valueOf(amount).multiply(taxRate(code, date, bondEtfExemptUntil))
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * 單筆試算：買進 shares 股，假設以 closePrice 全部賣出（1.0 的規則）。
     * 賣出日用 sellDate（null 代表不套用免徵）。
     */
    public static Result calculate(String code, int shares, BigDecimal buyPrice, BigDecimal closePrice,
                                   LocalDate sellDate, LocalDate bondEtfExemptUntil) {
        long buyAmount = amount(buyPrice, shares);
        long buyCost = buyAmount + fee(buyAmount);
        long sellAmount = amount(closePrice, shares);
        long sellFee = fee(sellAmount);
        long tax = tax(code, sellAmount, sellDate, bondEtfExemptUntil);
        long pnl = sellAmount - sellFee - tax - buyCost;
        return new Result(buyCost, sellAmount, pnl, returnPct(pnl, buyCost));
    }

    /** 收益率 = 盈虧 ÷ 分母 × 100，四捨五入到小數第二位；分母為 0 時回 0.00。 */
    public static BigDecimal returnPct(long pnl, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(pnl).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
