package com.example.holdings.pnl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 回報二：債券 ETF 停徵證交稅（證券交易稅條例第 2-1 條，至 2026-12-31）。 */
class TaxRulesTest {

    @ParameterizedTest(name = "{0} 在 {1} 賣出 → 稅率 {2}")
    @CsvSource({
            "2330,   2026-11-12, 0.003",
            "0050,   2026-11-12, 0.001",
            "00632R, 2026-11-12, 0.001",   // 反向型：一般 ETF
            "00679B, 2026-11-12, 0",       // 債券 ETF：停徵
            "00687B, 2017-01-01, 0",       // 停徵第一天
            "00679B, 2016-12-30, 0.001",   // 停徵前
            "00679B, 2026-12-31, 0",       // 停徵最後一天
            "00679B, 2027-01-04, 0.001",   // 現行條文到期後恢復課徵（除非延長）
            "00980D, 2026-11-12, 0.001"    // 主動式債券 ETF：現行條文不在停徵範圍
    })
    void rates(String code, String date, String rate) {
        assertThat(TaxRules.DEFAULT.rate(code, LocalDate.parse(date))).isEqualByComparingTo(rate);
    }

    @Test
    void extensionOnlyNeedsConfig() {
        TaxRules extended = new TaxRules(LocalDate.of(2036, 12, 31));
        assertThat(extended.rate("00679B", LocalDate.of(2027, 1, 4))).isEqualByComparingTo("0");
    }

    @Test
    void reportTwoSellingThreeLotsOf00679BHasNoTax() {
        // 回報二：上週賣出 00679B 三張（價格是測試用）
        var p = PositionCalculator.compute("00679B", List.of(
                new PositionCalculator.TradeInput(1, PositionCalculator.Side.BUY, LocalDate.of(2026, 3, 2), 3000, new BigDecimal("27.10")),
                new PositionCalculator.TradeInput(2, PositionCalculator.Side.SELL, LocalDate.of(2026, 11, 12), 3000, new BigDecimal("26.36"))),
                TaxRules.DEFAULT);
        PositionCalculator.TradeResult sell = p.trades().get(1);
        assertThat(sell.tax()).isEqualByComparingTo("0");
        assertThat(sell.fee()).isEqualByComparingTo("112"); // 79,080 × 0.1425% = 112.689 → 112
        // 1.0 會多扣 79 元稅（79,080 × 0.1%）
        assertThat(PnlCalculator.tax("00679B", new BigDecimal("79080"), LocalDate.of(2026, 11, 12), TaxRules.DEFAULT)).isZero();
    }
}
