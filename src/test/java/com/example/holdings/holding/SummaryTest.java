package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.holdings.pnl.PnlCalculator;
import com.example.holdings.pnl.PositionCalculator;
import com.example.holdings.pnl.TaxRules;
import com.example.holdings.quote.Market;

class SummaryTest {

    private static PositionView view(String code, long shares, String buy, String close, long realized) {
        var p = PositionCalculator.compute(code, List.of(new PositionCalculator.TradeInput(1,
                PositionCalculator.Side.BUY, LocalDate.of(2026, 11, 1), shares, new BigDecimal(buy))), TaxRules.DEFAULT);
        if (close == null) {
            return new PositionView(1, code, code, Market.TWSE, shares, p.avgCost(), p.avgPrice(), p.costYuan(),
                    null, null, null, null, null, realized, 0, List.of());
        }
        var r = PnlCalculator.unrealized(code, shares, p.cost(), new BigDecimal(close), LocalDate.of(2026, 11, 12), TaxRules.DEFAULT);
        return new PositionView(1, code, code, Market.TWSE, shares, p.avgCost(), p.avgPrice(), p.costYuan(),
                new BigDecimal(close), LocalDate.of(2026, 11, 12), r.marketValue(), r.pnl(), r.returnPct(), realized, 0, List.of());
    }

    @Test
    void sumsAllThreeExamples() {
        PortfolioSummary s = PortfolioQueryService.summarize(List.of(
                view("2330", 1000, "1000", "1100", 0), view("0050", 2000, "150", "145.5", 0), view("2363", 1000, "10", "10.5", 0)));
        assertThat(s.totalCost()).isEqualTo(1_001_425 + 300_427 + 10_020);
        assertThat(s.marketValue()).isEqualTo(1_100_000 + 291_000 + 10_500);
        assertThat(s.unrealizedPnl()).isEqualTo(93_708 - 10_132 + 429);
        assertThat(s.unrealizedReturnPct()).isEqualByComparingTo("6.40"); // 84005 ÷ 1311872 × 100 = 6.4034
        assertThat(s.quoteMissing()).isZero();
    }

    @Test
    void missingQuoteCountsInCostOnly() {
        PortfolioSummary s = PortfolioQueryService.summarize(List.of(view("2330", 1000, "1000", "1100", 0), view("6488", 1000, "600", null, 0)));
        assertThat(s.totalCost()).isEqualTo(1_001_425 + 600_855);
        assertThat(s.marketValue()).isEqualTo(1_100_000);
        assertThat(s.unrealizedPnl()).isEqualTo(93_708);
        assertThat(s.quoteMissing()).isEqualTo(1);
    }

    @Test
    void realizedIncludesClosedPositionsButCostDoesNot() {
        PositionView closed = new PositionView(2, "2363", "矽統", Market.TWSE, 0, null, null, 0,
                new BigDecimal("10.5"), LocalDate.of(2026, 11, 12), null, null, null, 1234, 0, List.of());
        PortfolioSummary s = PortfolioQueryService.summarize(List.of(view("2330", 1000, "1000", "1100", 500), closed));
        assertThat(s.realizedPnl()).isEqualTo(1734);
        assertThat(s.totalCost()).isEqualTo(1_001_425);
        assertThat(s.openPositions()).isEqualTo(1);
    }

    @Test
    void emptyIsAllZero() {
        PortfolioSummary s = PortfolioQueryService.summarize(List.of());
        assertThat(s.totalCost()).isZero();
        assertThat(s.unrealizedReturnPct()).isEqualByComparingTo("0");
    }
}
