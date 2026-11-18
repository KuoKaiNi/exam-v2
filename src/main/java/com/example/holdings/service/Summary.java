package com.example.holdings.service;

import com.example.holdings.domain.PnlCalculator;
import java.math.BigDecimal;
import java.util.List;

/**
 * 總計。取不到收盤價的持股不算進 marketValue 與 pnl，但算進 totalCost。
 */
public record Summary(long totalCost, long marketValue, long pnl, BigDecimal returnPct, int quoteMissing) {

    public static Summary of(List<HoldingView> holdings) {
        long totalCost = 0;
        long marketValue = 0;
        long pnl = 0;
        int missing = 0;
        for (HoldingView h : holdings) {
            totalCost += h.buyCost();
            if (h.pnl() == null) {
                missing++;
            } else {
                marketValue += h.marketValue();
                pnl += h.pnl();
            }
        }
        return new Summary(totalCost, marketValue, pnl, PnlCalculator.returnPct(pnl, totalCost), missing);
    }
}
