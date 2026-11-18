package com.example.holdings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TradeDtoV2(
        Long id,
        String side,
        String tradeDate,
        int shares,
        BigDecimal price,
        long amount,
        long fee,
        long tax,
        Long realizedPnl) {
}
