package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.example.holdings.quote.Market;

/**
 * 一個部位的計算結果。currentPrice 為 null 表示未取得收盤價，此時 marketValue、unrealized* 也是 null。
 * 全部賣光的部位 shares＝0、avgCost／avgPrice 為 null、cost＝0。
 *
 * @param avgCost   平均成本（含買進手續費），每股
 * @param avgPrice  平均買進價（不含手續費），每股；給 App 1.0 的 buy_price 用
 * @param cost      剩餘成本（含買進手續費），元
 */
public record PositionView(
        long id,
        String code,
        String name,
        Market market,
        long shares,
        BigDecimal avgCost,
        BigDecimal avgPrice,
        long cost,
        BigDecimal currentPrice,
        LocalDate priceDate,
        Long marketValue,
        Long unrealizedPnl,
        BigDecimal unrealizedReturnPct,
        long realizedPnl,
        long version,
        List<TradeView> trades) {

    public boolean isOpen() {
        return shares > 0;
    }
}
