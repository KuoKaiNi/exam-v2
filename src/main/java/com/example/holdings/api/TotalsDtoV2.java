package com.example.holdings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TotalsDtoV2(
        long costBasis,
        long marketValue,
        long unrealizedPnl,
        BigDecimal unrealizedReturnPct,
        long realizedPnl,
        int quoteMissing) {
}
