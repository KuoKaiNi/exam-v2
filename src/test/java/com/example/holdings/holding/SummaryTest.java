package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.holdings.pnl.PnlCalculator;
import com.example.holdings.pnl.Summary;
import com.example.holdings.quote.Market;

class SummaryTest {

    private static HoldingView view(String code, int lots, String buy, String close) {
        BigDecimal b = new BigDecimal(buy);
        if (close == null) {
            return new HoldingView(1, code, code, Market.TWSE, lots, b, null, null, PnlCalculator.cost(lots, b), null, null, null);
        }
        PnlCalculator.Result r = PnlCalculator.calculate(code, lots, b, new BigDecimal(close));
        return new HoldingView(1, code, code, Market.TWSE, lots, b, new BigDecimal(close), LocalDate.of(2026, 11, 12),
                r.cost(), r.marketValue(), r.pnl(), r.returnPct());
    }

    @Test
    void sumsAllThreeExamples() {
        Summary s = HoldingQueryService.summarize(List.of(
                view("2330", 1, "1000", "1100"), view("0050", 2, "150", "145.5"), view("2363", 1, "10", "10.5")));
        assertThat(s.totalCost()).isEqualTo(1_001_425 + 300_427 + 10_020);
        assertThat(s.marketValue()).isEqualTo(1_100_000 + 291_000 + 10_500);
        assertThat(s.pnl()).isEqualTo(93_708 - 10_132 + 429);
        assertThat(s.returnPct()).isEqualByComparingTo("6.40"); // 84005 ÷ 1311872 × 100 = 6.4034
        assertThat(s.quoteMissing()).isZero();
    }

    @Test
    void missingQuoteCountsInCostOnly() {
        Summary s = HoldingQueryService.summarize(List.of(view("2330", 1, "1000", "1100"), view("6488", 1, "600", null)));
        assertThat(s.totalCost()).isEqualTo(1_001_425 + 600_855);
        assertThat(s.marketValue()).isEqualTo(1_100_000);
        assertThat(s.pnl()).isEqualTo(93_708);
        assertThat(s.quoteMissing()).isEqualTo(1);
    }

    @Test
    void emptyIsAllZero() {
        Summary s = HoldingQueryService.summarize(List.of());
        assertThat(s.totalCost()).isZero();
        assertThat(s.returnPct()).isEqualByComparingTo("0");
    }
}
