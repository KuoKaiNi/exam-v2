package com.example.holdings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.holdings.quote.Market;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 總計的規則：取不到收盤價的部位不算市值與損益，但算成本；已全部賣掉的部位只算已實現損益。 */
class TotalsTest {

    @Test
    void 取不到收盤價的部位_算進成本_不算進市值與未實現損益() {
        PositionView priced = position(1L, 1000, 1_001_425L, 1_100_000L, 93_708L, 0L);
        PositionView unpriced = position(2L, 1000, 600_000L, null, null, 0L);

        Totals t = Totals.of(List.of(priced, unpriced));

        assertEquals(1_601_425L, t.costBasis());
        assertEquals(1_100_000L, t.marketValue());
        assertEquals(93_708L, t.unrealizedPnl());
        assertEquals(1, t.quoteMissing());
    }

    @Test
    void 全部賣掉的部位_只算已實現損益() {
        PositionView sold = position(3L, 0, 0L, null, null, 25_000L);

        Totals t = Totals.of(List.of(sold));

        assertEquals(0L, t.costBasis());
        assertEquals(25_000L, t.realizedPnl());
        assertEquals(0, t.quoteMissing());
        assertEquals(new BigDecimal("0.00"), t.unrealizedReturnPct());
    }

    private static PositionView position(Long id, int shares, long cost, Long marketValue, Long unrealized,
                                         long realized) {
        return new PositionView(id, "2330", "台積電", Market.TWSE, shares, cost, cost, null, null, realized,
                List.of(), null, null, marketValue, unrealized, null);
    }
}
