package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.pnl.PositionCalculator;

/**
 * 一筆交易與它的計算結果。金額單位元（四捨五入）。costBasis、realizedPnl 只有賣出有值。
 */
public record TradeView(
        long id,
        long holdingId,
        PositionCalculator.Side side,
        LocalDate date,
        boolean dateEstimated,
        long shares,
        BigDecimal price,
        long amount,
        long fee,
        long tax,
        Long costBasis,
        Long realizedPnl,
        long sharesAfter,
        long version) {
}
