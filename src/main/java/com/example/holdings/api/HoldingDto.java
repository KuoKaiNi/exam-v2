package com.example.holdings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

/** App 1.0 規格的持股物件，欄位名稱用小寫加底線。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HoldingDto(
        Long id,
        String code,
        String name,
        String market,
        int lots,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        String priceDate,
        Long pnl,
        BigDecimal returnPct) {
}
