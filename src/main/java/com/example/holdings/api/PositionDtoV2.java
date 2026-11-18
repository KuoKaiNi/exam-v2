package com.example.holdings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/** 2.0 的部位：股數用「股」，含交易明細。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PositionDtoV2(
        Long id,
        String code,
        String name,
        String market,
        int shares,
        BigDecimal avgPrice,
        BigDecimal avgCost,
        long costBasis,
        long realizedPnl,
        BigDecimal currentPrice,
        String priceDate,
        Long marketValue,
        Long unrealizedPnl,
        BigDecimal unrealizedReturnPct,
        List<TradeDtoV2> trades) {
}
