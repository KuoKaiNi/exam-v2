package com.example.holdings.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 持股帳本：依交易順序（日期、再依建立順序）用移動加權平均法算出剩餘成本與已實現損益。
 * 賣出時，剩餘成本按賣出股數比例轉出；比例結果四捨五入到元。
 * 任何一筆賣出超過當時持股，整個帳本都不成立，丟出 INSUFFICIENT_SHARES。
 */
public final class Ledger {

    /**
     * shares：剩餘股數；cost：剩餘成本（含買進手續費）；gross：剩餘的買進成交金額（不含費用，用來算成交均價）；
     * realized：已實現損益合計；realizedByTrade：每筆賣出的已實現損益。
     */
    public record Result(int shares, long cost, long gross, long realized, Map<Long, Long> realizedByTrade) {
    }

    private static final Comparator<Trade> ORDER = Comparator
            .comparing(Trade::getTradeDate, Comparator.nullsFirst(Comparator.<java.time.LocalDate>naturalOrder()))
            .thenComparing(Trade::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    private Ledger() {
    }

    /** 顯示與計算用的交易順序。 */
    public static List<Trade> ordered(List<Trade> trades) {
        List<Trade> sorted = new ArrayList<>(trades);
        sorted.sort(ORDER);
        return sorted;
    }

    public static Result replay(List<Trade> trades) {
        int shares = 0;
        long cost = 0;
        long gross = 0;
        long realized = 0;
        Map<Long, Long> perTrade = new HashMap<>();
        for (Trade t : ordered(trades)) {
            int q = t.getShares();
            if (t.getSide() == Side.BUY) {
                shares += q;
                cost += t.getAmount() + t.getFee();
                gross += t.getAmount();
            } else {
                if (q > shares) {
                    throw new HoldingException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_SHARES",
                            "交易日 " + dateText(t) + " 賣出 " + q + " 股，但當時只剩 " + shares + " 股");
                }
                long releasedCost = q == shares ? cost : prorate(cost, q, shares);
                long releasedGross = q == shares ? gross : prorate(gross, q, shares);
                long pnl = t.getAmount() - t.getFee() - t.getTax() - releasedCost;
                perTrade.put(t.getId(), pnl);
                realized += pnl;
                cost -= releasedCost;
                gross -= releasedGross;
                shares -= q;
            }
        }
        return new Result(shares, cost, gross, realized, perTrade);
    }

    static long prorate(long amount, int part, int whole) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(part))
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static String dateText(Trade t) {
        return t.getTradeDate() == null ? "（未記錄日期）" : t.getTradeDate().toString();
    }
}
