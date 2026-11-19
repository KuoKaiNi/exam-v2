package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.quote.Market;

/**
 * 清單上的一筆：持股＋快取裡的名稱與收盤價＋盈虧。
 * currentPrice、priceDate、pnl、returnPct 為 null 表示未取得收盤價。cost 一定有值。
 */
public record HoldingView(
        long id,
        String code,
        String name,
        Market market,
        int lots,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        LocalDate priceDate,
        long cost,
        Long marketValue,
        Long pnl,
        BigDecimal returnPct) {
}
