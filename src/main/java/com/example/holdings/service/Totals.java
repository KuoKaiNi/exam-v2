package com.example.holdings.service;

import com.example.holdings.domain.PnlCalculator;
import java.math.BigDecimal;
import java.util.List;

/**
 * 總計。未實現損益只算剩餘股數；取不到收盤價的部位不算進市值與未實現損益，但算進成本。
 * 收益率 = 未實現損益 ÷ 剩餘成本 × 100（與 1.0 相同的分母規則）。
 */
public record Totals(long costBasis, long marketValue, long unrealizedPnl, BigDecimal unrealizedReturnPct,
                     long realizedPnl, int quoteMissing) {

    public static Totals of(List<PositionView> positions) {
        long cost = 0;
        long market = 0;
        long unrealized = 0;
        long realized = 0;
        int missing = 0;
        for (PositionView p : positions) {
            cost += p.costBasis();
            realized += p.realizedPnl();
            if (p.shares() > 0) {
                if (p.unrealizedPnl() == null) {
                    missing++;
                } else {
                    market += p.marketValue();
                    unrealized += p.unrealizedPnl();
                }
            }
        }
        return new Totals(cost, market, unrealized, PnlCalculator.returnPct(unrealized, cost), realized, missing);
    }
}
