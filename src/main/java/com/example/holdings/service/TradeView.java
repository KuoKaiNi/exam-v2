package com.example.holdings.service;

import com.example.holdings.domain.Side;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 一筆買進或賣出。realizedPnl 只有賣出才有值。 */
public record TradeView(
        Long id,
        Side side,
        LocalDate tradeDate,
        int shares,
        BigDecimal price,
        long amount,
        long fee,
        long tax,
        Long realizedPnl) {
}
