package com.example.holdings.pnl;

import static com.example.holdings.pnl.PositionCalculator.Side.BUY;
import static com.example.holdings.pnl.PositionCalculator.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.holdings.pnl.PositionCalculator.Position;
import com.example.holdings.pnl.PositionCalculator.TradeInput;

/**
 * 2.0 計算規則的單元測試：分次買進、平均成本（含手續費）、部分賣出的已實現／未實現損益、零股。
 * 預期值是手算（Python Decimal 驗算）的結果，不是從程式輸出抄來的。價格是測試用，不是真實股價。
 */
class PositionCalculatorTest {

    private static TradeInput t(long id, PositionCalculator.Side side, String date, long shares, String price) {
        return new TradeInput(id, side, LocalDate.parse(date), shares, new BigDecimal(price));
    }

    private static final List<TradeInput> THREE_BUYS = List.of(
            t(1, BUY, "2026-01-05", 1000, "1000"),   // 1,000,000 + 1,425
            t(2, BUY, "2026-02-10", 500, "1100"),    //   550,000 +   783（783.75 捨去）
            t(3, BUY, "2026-03-01", 300, "1200"));   //   360,000 +   513

    @Test
    void multipleBuysGiveTotalSharesAndAverageCostIncludingFees() {
        Position p = PositionCalculator.compute("2330", THREE_BUYS, TaxRules.DEFAULT);
        assertThat(p.shares()).isEqualTo(1800);
        assertThat(p.cost()).isEqualByComparingTo("1912721");
        assertThat(p.avgCost()).isEqualByComparingTo("1062.62");   // 1,912,721 ÷ 1,800
        assertThat(p.avgPrice()).isEqualByComparingTo("1061.11");  // 1,910,000 ÷ 1,800
        assertThat(p.realizedPnl()).isEqualByComparingTo("0");
    }

    @Test
    void partialSellRealizesPnlWithMovingAverageCost() {
        List<TradeInput> trades = new java.util.ArrayList<>(THREE_BUYS);
        trades.add(t(4, SELL, "2026-04-01", 900, "1300"));
        Position p = PositionCalculator.compute("2330", trades, TaxRules.DEFAULT);

        // 賣出 1,170,000，手續費 1,667（1,667.25），證交稅 3,510；賣掉的成本 1,912,721 × 900/1,800 = 956,360.50
        PositionCalculator.TradeResult sell = p.trades().get(3);
        assertThat(sell.fee()).isEqualByComparingTo("1667");
        assertThat(sell.tax()).isEqualByComparingTo("3510");
        assertThat(sell.costBasis()).isEqualByComparingTo("956360.50");
        assertThat(sell.realizedPnl()).isEqualByComparingTo("208462.50");
        assertThat(p.realizedYuan()).isEqualTo(208_463);

        assertThat(p.shares()).isEqualTo(900);
        assertThat(p.cost()).isEqualByComparingTo("956360.50");
        assertThat(p.avgCost()).isEqualByComparingTo("1062.62"); // 賣出不改變平均成本

        // 剩下 900 股用收盤 1,400 算未實現：賣出 1,260,000，手續費 1,795，證交稅 3,780
        PnlCalculator.Result u = PnlCalculator.unrealized("2330", p.shares(), p.cost(), new BigDecimal("1400"),
                LocalDate.of(2026, 4, 2), TaxRules.DEFAULT);
        assertThat(u.pnl()).isEqualTo(298_065);          // 298,064.50 四捨五入
        assertThat(u.returnPct()).isEqualByComparingTo("31.17");
        assertThat(u.marketValue()).isEqualTo(1_260_000);
        assertThat(u.cost()).isEqualTo(956_361);
    }

    @Test
    void sellingEverythingLeavesZeroCost() {
        Position p = PositionCalculator.compute("2330", List.of(
                t(1, BUY, "2026-01-05", 3, "10"),
                t(2, SELL, "2026-01-06", 1, "10"),
                t(3, SELL, "2026-01-07", 1, "10"),
                t(4, SELL, "2026-01-08", 1, "10")), TaxRules.DEFAULT);
        assertThat(p.shares()).isZero();
        assertThat(p.cost()).isEqualByComparingTo("0");
        assertThat(p.avgCost()).isNull();
        // 買 30 + 手續費 20；三次賣出各收 20 手續費、稅 0 → 30 − 60 − 50 = −80
        assertThat(p.realizedPnl()).isEqualByComparingTo("-80");
    }

    @Test
    void oddLotsUseSharesAndMinimumFee() {
        Position p = PositionCalculator.compute("2330", List.of(t(1, BUY, "2026-01-05", 300, "10.05")), TaxRules.DEFAULT);
        assertThat(p.shares()).isEqualTo(300);
        assertThat(p.cost()).isEqualByComparingTo("3035.00"); // 3,015 + 最低手續費 20
        assertThat(p.avgCost()).isEqualByComparingTo("10.12");
    }

    @Test
    void cannotSellMoreThanHeldAtThatTime() {
        assertThatThrownBy(() -> PositionCalculator.compute("2330", List.of(
                t(1, BUY, "2026-01-05", 1000, "10"),
                t(2, SELL, "2026-01-06", 1001, "10")), TaxRules.DEFAULT))
                .isInstanceOf(OversellException.class)
                .hasMessageContaining("1001").hasMessageContaining("1000");

        // 總數夠，但賣出日期早於買進
        assertThatThrownBy(() -> PositionCalculator.compute("2330", List.of(
                t(1, BUY, "2026-02-01", 1000, "10"),
                t(2, SELL, "2026-01-06", 500, "10")), TaxRules.DEFAULT))
                .isInstanceOf(OversellException.class);
    }

    @Test
    void sameDayBuyIsCountedBeforeSell() {
        Position p = PositionCalculator.compute("2330", List.of(
                t(5, SELL, "2026-01-05", 1000, "11"),
                t(9, BUY, "2026-01-05", 1000, "10")), TaxRules.DEFAULT);
        assertThat(p.shares()).isZero();
    }

    @Test
    void singleBuyMatchesVersion1Examples() {
        // 1.0 的三個例子用 2.0 的計算方式也要得到一樣的結果
        Object[][] cases = {
                {"2330", 1000L, "1000.00", "1100.00", 93_708L, "9.36"},
                {"0050", 2000L, "150.00", "145.50", -10_132L, "-3.37"},
                {"2363", 1000L, "10.00", "10.50", 429L, "4.28"}};
        for (Object[] c : cases) {
            Position p = PositionCalculator.compute((String) c[0],
                    List.of(t(1, BUY, "2026-11-01", (Long) c[1], (String) c[2])), TaxRules.DEFAULT);
            PnlCalculator.Result r = PnlCalculator.unrealized((String) c[0], p.shares(), p.cost(),
                    new BigDecimal((String) c[3]), LocalDate.of(2026, 11, 12), TaxRules.DEFAULT);
            assertThat(r.pnl()).as((String) c[0]).isEqualTo(c[4]);
            assertThat(r.returnPct()).as((String) c[0]).isEqualByComparingTo((String) c[5]);
        }
    }
}
