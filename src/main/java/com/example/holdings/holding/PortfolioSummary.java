package com.example.holdings.holding;

import java.math.BigDecimal;

/**
 * 2.0 總計。totalCost、marketValue、unrealizedPnl 只算還有股數的部位；取不到收盤價的部位
 * 不算進 marketValue 與 unrealizedPnl，但算進 totalCost（同 1.0 規則）。realizedPnl 是所有部位（含已賣光）的已實現損益。
 */
public record PortfolioSummary(long totalCost, long marketValue, long unrealizedPnl, BigDecimal unrealizedReturnPct,
                               long realizedPnl, int quoteMissing, int openPositions) {
}
