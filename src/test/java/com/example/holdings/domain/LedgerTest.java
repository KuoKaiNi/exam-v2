package com.example.holdings.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 移動加權平均帳本。全部用手算的數字當預期值，不連網路。 */
class LedgerTest {

    @Test
    void 分三次買進_成本含手續費_平均成本() {
        // 1000 股 @ 100：金額 100,000，手續費 142 → 成本 100,142
        // 500 股 @ 110：金額 55,000，手續費 78 → 成本 55,078
        // 500 股 @ 120：金額 60,000，手續費 85 → 成本 60,085
        Ledger.Result r = Ledger.replay(List.of(
                buy(1L, "2026-11-01", 1000, "100.00"),
                buy(2L, "2026-11-02", 500, "110.00"),
                buy(3L, "2026-11-03", 500, "120.00")));

        assertEquals(2000, r.shares());
        assertEquals(100142L + 55078L + 60085L, r.cost());
        assertEquals(215_305L, r.cost());
        assertEquals(215_000L, r.gross());
        assertEquals(0L, r.realized());
    }

    @Test
    void 賣出一部分_按比例轉出成本_算出已實現損益() {
        // 買 1000 股 @ 100（成本 100,142），賣 400 股 @ 105
        // 轉出成本 100,142 × 400 / 1000 = 40,056.8 → 40,057
        // 賣出金額 42,000，手續費 59，證交稅 126 → 已實現 42,000 - 59 - 126 - 40,057 = 1,758
        Ledger.Result r = Ledger.replay(List.of(
                buy(1L, "2026-11-01", 1000, "100.00"),
                sell(2L, "2026-11-05", 400, "105.00", 59, 126)));

        assertEquals(600, r.shares());
        assertEquals(100142L - 40057L, r.cost());
        assertEquals(1758L, r.realizedByTrade().get(2L));
        assertEquals(1758L, r.realized());
    }

    @Test
    void 全部賣掉_剩餘成本歸零() {
        Ledger.Result r = Ledger.replay(List.of(
                buy(1L, "2026-11-01", 300, "50.00"),
                sell(2L, "2026-11-02", 300, "52.00", 22, 46)));

        assertEquals(0, r.shares());
        assertEquals(0L, r.cost());
        assertEquals(0L, r.gross());
        // 買進 15,000 + 手續費 21（21.375 無條件捨去）= 15,021；賣出 15,600 - 22 - 46 - 15,021 = 511
        assertEquals(511L, r.realized());
    }

    @Test
    void 賣超過持股_丟出INSUFFICIENT_SHARES() {
        HoldingException e = assertThrows(HoldingException.class, () -> Ledger.replay(List.of(
                buy(1L, "2026-11-01", 300, "50.00"),
                sell(2L, "2026-11-02", 301, "52.00", 22, 46))));
        assertEquals("INSUFFICIENT_SHARES", e.getErrorCode());
    }

    @Test
    void 日期順序影響帳本_先賣後買不成立() {
        // 賣出日期早於買進日期：當時沒有股票可賣
        assertThrows(HoldingException.class, () -> Ledger.replay(List.of(
                buy(1L, "2026-11-05", 1000, "100.00"),
                sell(2L, "2026-11-01", 1000, "105.00", 0, 0))));
    }

    @Test
    void 升級來的買進沒有日期_照常計入成本() {
        Ledger.Result r = Ledger.replay(List.of(
                buy(1L, "2026-11-01", 1000, "100.00"),
                legacyBuy(2L, 1000, "90.00")));
        assertEquals(2000, r.shares());
        // 90 元那筆：90,000 + 手續費 128 = 90,128；100 元那筆：100,000 + 142 = 100,142
        assertEquals(90_128L + 100_142L, r.cost());
    }

    private static Trade buy(Long id, String date, int shares, String price) {
        long amount = PnlCalculator.amount(new BigDecimal(price), shares);
        return withId(new Trade(1L, Side.BUY, LocalDate.parse(date), shares, new BigDecimal(price),
                amount, PnlCalculator.fee(amount), 0), id);
    }

    private static Trade legacyBuy(Long id, int shares, String price) {
        long amount = PnlCalculator.amount(new BigDecimal(price), shares);
        return withId(new Trade(1L, Side.BUY, null, shares, new BigDecimal(price),
                amount, PnlCalculator.fee(amount), 0), id);
    }

    private static Trade sell(Long id, String date, int shares, String price, long fee, long tax) {
        long amount = PnlCalculator.amount(new BigDecimal(price), shares);
        return withId(new Trade(1L, Side.SELL, LocalDate.parse(date), shares, new BigDecimal(price),
                amount, fee, tax), id);
    }

    /** 測試用：補上 id（實際寫入後由資料庫產生）。 */
    private static Trade withId(Trade t, Long id) {
        try {
            var f = Trade.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
            return t;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
