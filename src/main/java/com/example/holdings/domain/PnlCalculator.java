package com.example.holdings.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 盈虧：假設現在用收盤價全部賣出，扣掉買賣手續費與證交稅後淨賺多少。
 * 所有金額以元為單位，手續費與稅都是無條件捨去到元。
 */
public final class PnlCalculator {

    static final int SHARES_PER_LOT = 1000;
    static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    static final BigDecimal MIN_FEE = BigDecimal.valueOf(20);
    static final BigDecimal STOCK_TAX_RATE = new BigDecimal("0.003");
    static final BigDecimal ETF_TAX_RATE = new BigDecimal("0.001");

    /**
     * buyCost：買進金額＋買進手續費（收益率的分母）。
     * marketValue：收盤價×股數，不扣費用。
     * pnl：淨盈虧（元）。
     * returnPct：收益率（%），四捨五入到小數第二位。
     */
    public record Result(long buyCost, long marketValue, long pnl, BigDecimal returnPct) {
    }

    private PnlCalculator() {
    }

    public static Result calculate(String code, int lots, BigDecimal buyPrice, BigDecimal closePrice) {
        BigDecimal shares = BigDecimal.valueOf((long) lots * SHARES_PER_LOT);

        BigDecimal buyAmount = buyPrice.multiply(shares);
        BigDecimal buyFee = fee(buyAmount);
        BigDecimal buyCost = buyAmount.add(buyFee);

        BigDecimal sellAmount = closePrice.multiply(shares);
        BigDecimal sellFee = fee(sellAmount);
        BigDecimal tax = sellAmount.multiply(taxRate(code)).setScale(0, RoundingMode.DOWN);

        BigDecimal pnl = sellAmount.subtract(sellFee).subtract(tax).subtract(buyCost);

        return new Result(
                toYuan(buyCost),
                toYuan(sellAmount),
                toYuan(pnl),
                returnPct(toYuan(pnl), toYuan(buyCost)));
    }

    /** 買進金額＋買進手續費，不需要收盤價。取不到收盤價的持股也用它算總成本。 */
    public static long buyCost(int lots, BigDecimal buyPrice) {
        BigDecimal buyAmount = buyPrice.multiply(BigDecimal.valueOf((long) lots * SHARES_PER_LOT));
        return toYuan(buyAmount.add(fee(buyAmount)));
    }

    /** 收益率 = 盈虧 ÷ 分母 × 100，四捨五入到小數第二位；分母為 0 時回 0.00。 */
    public static BigDecimal returnPct(long pnl, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(pnl).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** 手續費：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元。 */
    static BigDecimal fee(BigDecimal amount) {
        BigDecimal raw = amount.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);
        return raw.max(MIN_FEE);
    }

    /** ETF（代號 00 開頭）0.1%，一般股票 0.3%。 */
    static BigDecimal taxRate(String code) {
        return code.startsWith("00") ? ETF_TAX_RATE : STOCK_TAX_RATE;
    }

    /** 股數是 1000 的倍數、價格最多兩位小數，金額一定是整數元；這裡只是把 scale 收成 0。 */
    private static long toYuan(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
