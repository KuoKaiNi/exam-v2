package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.quote.Market;

/** 清單上的一筆：持股＋快取裡的名稱與收盤價。currentPrice 為 null 表示未取得。 */
public record HoldingView(
        long id,
        String code,
        String name,
        Market market,
        int lots,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        LocalDate priceDate) {
}
