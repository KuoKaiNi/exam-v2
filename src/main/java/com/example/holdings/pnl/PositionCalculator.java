package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 2.0 的部位計算：同一檔分多次買進、部分賣出。純計算，不碰資料庫或網路。
 *
 * <p>規則：
 * <ul>
 * <li>每一筆交易各自算手續費（成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元）；賣出另收證交稅（見 {@link TaxRules}）。</li>
 * <li>成本用<b>移動平均法</b>：剩餘成本＝所有買進的（成交金額＋手續費）減掉已賣出部分的成本。
 *     賣出 s 股時，賣掉的成本＝剩餘成本 × s ÷ 當時股數（四捨五入到分），剩下的成本留給剩下的股數，全部賣完時成本剛好歸零。</li>
 * <li>平均成本（含買進手續費）＝剩餘成本 ÷ 剩餘股數。</li>
 * <li>已實現損益（每筆賣出）＝成交金額 − 手續費 − 證交稅 − 賣掉的成本。</li>
 * <li>交易依日期排序；同一天先算買進、再算賣出；同一天同方向依輸入順序（id）。任何時點股數不可小於 0。</li>
 * </ul>
 */
public final class PositionCalculator {

    public enum Side { BUY, SELL }

    public record TradeInput(long id, Side side, LocalDate date, long shares, BigDecimal price) {
    }

    /** 每筆交易的計算結果。costBasis、realizedPnl 只有賣出才有值。sharesAfter 是這筆之後的持有股數。 */
    public record TradeResult(TradeInput trade, BigDecimal amount, BigDecimal fee, BigDecimal tax,
                              BigDecimal costBasis, BigDecimal realizedPnl, long sharesAfter) {
    }

    /**
     * @param shares      剩餘股數
     * @param cost        剩餘成本（含買進手續費），元，保留到分
     * @param buyAmount   剩餘的買進成交金額（不含手續費），用來算平均買進價
     * @param realizedPnl 已實現損益合計
     * @param trades      依計算順序排列的每筆結果
     */
    public record Position(long shares, BigDecimal cost, BigDecimal buyAmount, BigDecimal realizedPnl,
                           List<TradeResult> trades) {

        /** 平均成本（含買進手續費），每股，四捨五入到小數第二位；沒有持股時為 null。 */
        public BigDecimal avgCost() {
            return shares == 0 ? null : cost.divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
        }

        /** 平均買進價（不含手續費），每股，四捨五入到小數第二位；沒有持股時為 null。 */
        public BigDecimal avgPrice() {
            return shares == 0 ? null : buyAmount.divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
        }

        public long costYuan() {
            return PnlCalculator.toYuan(cost);
        }

        public long realizedYuan() {
            return PnlCalculator.toYuan(realizedPnl);
        }
    }

    public static final Comparator<TradeInput> ORDER = Comparator
            .comparing(TradeInput::date)
            .thenComparing(t -> t.side() == Side.BUY ? 0 : 1)
            .thenComparingLong(TradeInput::id);

    private PositionCalculator() {
    }

    /** @throws OversellException 某一筆賣出超過當時持有股數 */
    public static Position compute(String code, List<TradeInput> trades, TaxRules rules) {
        List<TradeInput> sorted = new ArrayList<>(trades);
        sorted.sort(ORDER);
        long shares = 0;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal buyAmount = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        List<TradeResult> results = new ArrayList<>();
        for (TradeInput t : sorted) {
            BigDecimal amount = t.price().multiply(BigDecimal.valueOf(t.shares()));
            BigDecimal fee = PnlCalculator.fee(amount);
            if (t.side() == Side.BUY) {
                shares += t.shares();
                cost = cost.add(amount).add(fee);
                buyAmount = buyAmount.add(amount);
                results.add(new TradeResult(t, amount, fee, BigDecimal.ZERO, null, null, shares));
            } else {
                if (t.shares() > shares) {
                    throw new OversellException(t.date(), t.shares(), shares);
                }
                BigDecimal tax = PnlCalculator.tax(code, amount, t.date(), rules);
                BigDecimal basis;
                BigDecimal amountOut;
                if (t.shares() == shares) {
                    basis = cost;
                    amountOut = buyAmount;
                } else {
                    BigDecimal ratio = BigDecimal.valueOf(t.shares());
                    BigDecimal total = BigDecimal.valueOf(shares);
                    basis = cost.multiply(ratio).divide(total, 2, RoundingMode.HALF_UP);
                    amountOut = buyAmount.multiply(ratio).divide(total, 2, RoundingMode.HALF_UP);
                }
                BigDecimal pnl = amount.subtract(fee).subtract(tax).subtract(basis);
                shares -= t.shares();
                cost = cost.subtract(basis);
                buyAmount = buyAmount.subtract(amountOut);
                realized = realized.add(pnl);
                results.add(new TradeResult(t, amount, fee, tax, basis, pnl, shares));
            }
        }
        return new Position(shares, cost, buyAmount, realized, results);
    }
}
