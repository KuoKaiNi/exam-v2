package com.example.holdings.pnl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 純計算的單元測試，不連網路、不用資料庫。前三個例子是題目指定的（價格是測試用，不是真實股價）。
 */
class PnlCalculatorTest {

    @ParameterizedTest(name = "{0}：{1} {2} 張，買 {3} 收 {4} → 盈虧 {5}，收益率 {6}%")
    @CsvSource({
            "一般股票,          2330, 1, 1000.00, 1100.00,  93708,  9.36",
            "ETF,               0050, 2,  150.00,  145.50, -10132, -3.37",
            "手續費低於 20 元,   2363, 1,   10.00,   10.50,    429,  4.28"
    })
    void examplesFromTheExam(String label, String code, int lots, String buy, String close, long pnl, String pct) {
        PnlCalculator.Result r = PnlCalculator.calculate(code, lots, new BigDecimal(buy), new BigDecimal(close));
        assertThat(r.pnl()).isEqualTo(pnl);
        assertThat(r.returnPct()).isEqualByComparingTo(pct);
    }

    @Test
    void generalStockBreakdown() {
        // 買進 1,000,000，手續費 1,425；賣出 1,100,000，手續費 1,567.5→1,567，證交稅 3,300
        PnlCalculator.Result r = PnlCalculator.calculate("2330", 1, new BigDecimal("1000"), new BigDecimal("1100"));
        assertThat(r.cost()).isEqualTo(1_001_425);
        assertThat(r.marketValue()).isEqualTo(1_100_000);
        assertThat(r.pnl()).isEqualTo(1_100_000 - 1_567 - 3_300 - 1_000_000 - 1_425);
    }

    @Test
    void etfUsesLowerTax() {
        // 賣出 291,000：ETF 稅 291；若當一般股票會是 873
        PnlCalculator.Result etf = PnlCalculator.calculate("0050", 2, new BigDecimal("150"), new BigDecimal("145.5"));
        PnlCalculator.Result stock = PnlCalculator.calculate("2050", 2, new BigDecimal("150"), new BigDecimal("145.5"));
        assertThat(stock.pnl() - etf.pnl()).isEqualTo(-(873 - 291));
        assertThat(PnlCalculator.isEtf("00632R")).isTrue();
        assertThat(PnlCalculator.isEtf("2330")).isFalse();
        assertThat(PnlCalculator.isEtf("6005")).isFalse(); // 0 開頭但不是 00
    }

    @Test
    void feeIsFlooredWithMinimum20() {
        assertThat(PnlCalculator.fee(new BigDecimal("10000"))).isEqualByComparingTo("20");      // 14.25 → 最低 20
        assertThat(PnlCalculator.fee(new BigDecimal("14035"))).isEqualByComparingTo("20");      // 19.999… → 19 → 20
        assertThat(PnlCalculator.fee(new BigDecimal("14036"))).isEqualByComparingTo("20");      // 20.0013 → 20
        assertThat(PnlCalculator.fee(new BigDecimal("1100000"))).isEqualByComparingTo("1567");  // 1567.5 → 1567
    }

    @Test
    void returnPctRoundsHalfUpToTwoDecimals() {
        assertThat(PnlCalculator.returnPct(new BigDecimal("1"), new BigDecimal("800"))).isEqualByComparingTo("0.13"); // 0.125
        assertThat(PnlCalculator.returnPct(new BigDecimal("-1"), new BigDecimal("800"))).isEqualByComparingTo("-0.13");
        assertThat(PnlCalculator.returnPct(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void costDoesNotNeedClosePrice() {
        assertThat(PnlCalculator.cost(1, new BigDecimal("10"))).isEqualTo(10_020);
        assertThat(PnlCalculator.cost(1, new BigDecimal("1066.67"))).isEqualTo(1_066_670 + 1_520);
    }
}
