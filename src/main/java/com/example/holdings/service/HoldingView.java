package com.example.holdings.service;

import com.example.holdings.quote.Market;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 清單一列的資料：持股欄位加上最近收盤價（查不到時為 null）。 */
public record HoldingView(
        Long id,
        String code,
        String name,
        Market market,
        int lots,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        LocalDate priceDate) {
}
