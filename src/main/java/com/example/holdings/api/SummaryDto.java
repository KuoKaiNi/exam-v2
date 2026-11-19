package com.example.holdings.api;

import java.math.BigDecimal;

import com.example.holdings.pnl.Summary;
import com.fasterxml.jackson.annotation.JsonProperty;

/** App 1.0 的「總計物件」。 */
public record SummaryDto(
        @JsonProperty("total_cost") long totalCost,
        @JsonProperty("market_value") long marketValue,
        @JsonProperty("pnl") long pnl,
        @JsonProperty("return_pct") BigDecimal returnPct,
        @JsonProperty("quote_missing") int quoteMissing) {

    public static SummaryDto from(Summary s) {
        return new SummaryDto(s.totalCost(), s.marketValue(), s.pnl(), s.returnPct(), s.quoteMissing());
    }
}
