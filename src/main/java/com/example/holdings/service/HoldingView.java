package com.example.holdings.service;

import com.example.holdings.quote.Market;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 清單一列的資料：持股欄位、最近收盤價與盈虧（取不到收盤價時收盤價、盈虧、收益率為 null）。 */
public record HoldingView(
        Long id,
        String code,
        String name,
        Market market,
        int lots,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        LocalDate priceDate,
        long buyCost,
        Long marketValue,
        Long pnl,
        BigDecimal returnPct) {
}
