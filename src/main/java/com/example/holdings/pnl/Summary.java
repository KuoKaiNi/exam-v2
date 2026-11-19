package com.example.holdings.pnl;

import java.math.BigDecimal;

/**
 * 總計。取不到收盤價的持股不算進 marketValue 和 pnl，但算進 totalCost；quoteMissing 是這種持股的筆數。
 */
public record Summary(long totalCost, long marketValue, long pnl, BigDecimal returnPct, int quoteMissing) {
}
