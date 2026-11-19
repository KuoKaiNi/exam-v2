package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 盈虧計算：假設現在用收盤價全部賣出，淨賺多少。純計算，不碰資料庫或網路。
 * <ul>
 * <li>手續費：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元；買進、賣出各收一次</li>
 * <li>證交稅：只有賣出收。一般股票 0.3%，ETF（代號 00 開頭）0.1%；無條件捨去到元</li>
 * <li>盈虧 = 賣出金額 − 賣出手續費 − 證交稅 − 買進金額 − 買進手續費</li>
 * <li>收益率 = 盈虧 ÷（買進金額 ＋ 買進手續費）× 100，四捨五入到小數第二位</li>
 * </ul>
 */
public final class PnlCalculator {

    public static final int SHARES_PER_LOT = 1000;
    static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    static final BigDecimal MIN_FEE = new BigDecimal("20");
    static final BigDecimal STOCK_TAX_RATE = new BigDecimal("0.003");
    static final BigDecimal ETF_TAX_RATE = new BigDecimal("0.001");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PnlCalculator() {
    }

    /** 買進成本＝買進金額＋買進手續費（元）。不需要收盤價。 */
    public static long cost(int lots, BigDecimal buyPrice) {
        BigDecimal buyAmount = amount(lots, buyPrice);
        return buyAmount.add(fee(buyAmount)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static Result calculate(String code, int lots, BigDecimal buyPrice, BigDecimal closePrice) {
        BigDecimal buyAmount = amount(lots, buyPrice);
        BigDecimal buyFee = fee(buyAmount);
        BigDecimal sellAmount = amount(lots, closePrice);
        BigDecimal sellFee = fee(sellAmount);
        BigDecimal tax = floor(sellAmount.multiply(isEtf(code) ? ETF_TAX_RATE : STOCK_TAX_RATE));

        BigDecimal cost = buyAmount.add(buyFee);
        BigDecimal pnl = sellAmount.subtract(sellFee).subtract(tax).subtract(cost);
        return new Result(
                cost.setScale(0, RoundingMode.HALF_UP).longValueExact(),
                sellAmount.setScale(0, RoundingMode.HALF_UP).longValueExact(),
                pnl.setScale(0, RoundingMode.HALF_UP).longValueExact(),
                returnPct(pnl, cost));
    }

    /** 收益率（%），四捨五入到小數第二位。成本為 0 時回 0.00。 */
    public static BigDecimal returnPct(BigDecimal pnl, BigDecimal cost) {
        if (cost.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return pnl.multiply(HUNDRED).divide(cost, 2, RoundingMode.HALF_UP);
    }

    public static boolean isEtf(String code) {
        return code != null && code.startsWith("00");
    }

    /** 手續費：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元。 */
    public static BigDecimal fee(BigDecimal tradeAmount) {
        return floor(tradeAmount.multiply(FEE_RATE)).max(MIN_FEE);
    }

    /** 證交稅：成交金額 × 稅率（依代號與賣出日期），無條件捨去到元。 */
    public static BigDecimal tax(String code, BigDecimal sellAmount, java.time.LocalDate sellDate, TaxRules rules) {
        return floor(sellAmount.multiply(rules.rate(code, sellDate)));
    }

    /**
     * 未實現損益：假設在 sellDate 用收盤價把剩下的 shares 股全部賣出。
     *
     * @param cost 剩餘成本（含買進手續費）
     */
    public static Result unrealized(String code, long shares, BigDecimal cost, BigDecimal closePrice,
                                    java.time.LocalDate sellDate, TaxRules rules) {
        BigDecimal sellAmount = closePrice.multiply(BigDecimal.valueOf(shares));
        BigDecimal pnl = sellAmount.subtract(fee(sellAmount)).subtract(tax(code, sellAmount, sellDate, rules)).subtract(cost);
        return new Result(toYuan(cost), toYuan(sellAmount), toYuan(pnl), returnPct(pnl, cost));
    }

    /** 金額四捨五入到元。 */
    public static long toYuan(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal amount(int lots, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf((long) lots * SHARES_PER_LOT));
    }

    private static BigDecimal floor(BigDecimal v) {
        return v.setScale(0, RoundingMode.FLOOR);
    }

    /**
     * @param cost        買進金額＋買進手續費（元）
     * @param marketValue 收盤價 × 股數，不扣費用（元）
     * @param pnl         盈虧（元）
     * @param returnPct   收益率（%）
     */
    public record Result(long cost, long marketValue, long pnl, BigDecimal returnPct) {
    }
}
