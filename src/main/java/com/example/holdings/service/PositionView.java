package com.example.holdings.service;

import com.example.holdings.quote.Market;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一檔股票的部位：剩餘股數與成本、已實現損益、交易明細，以及用最近收盤價算出的未實現損益。
 * 取不到收盤價時 currentPrice、priceDate、marketValue、unrealizedPnl、unrealizedReturnPct 為 null。
 */
public record PositionView(
        Long id,
        String code,
        String name,
        Market market,
        int shares,
        long costBasis,
        long grossAmount,
        BigDecimal avgPrice,
        BigDecimal avgCost,
        long realizedPnl,
        List<TradeView> trades,
        BigDecimal currentPrice,
        LocalDate priceDate,
        Long marketValue,
        Long unrealizedPnl,
        BigDecimal unrealizedReturnPct) {

    /** 1.0 只看整張（股數是 1000 的倍數）且還有股數的部位。 */
    public boolean wholeLotsForV1() {
        return shares > 0 && shares % 1000 == 0;
    }
}
