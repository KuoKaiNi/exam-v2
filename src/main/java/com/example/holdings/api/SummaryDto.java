package com.example.holdings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummaryDto(
        long totalCost,
        long marketValue,
        long pnl,
        BigDecimal returnPct,
        int quoteMissing) {
}
