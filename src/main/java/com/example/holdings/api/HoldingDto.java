package com.example.holdings.api;

import java.math.BigDecimal;

import com.example.holdings.holding.HoldingView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** App 1.0 的「持股物件」。欄位名稱、型別照規格；取不到收盤價時 current_price、price_date、pnl、return_pct 為 null（欄位仍要出現）。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HoldingDto(
        @JsonProperty("id") long id,
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("market") String market,
        @JsonProperty("lots") int lots,
        @JsonProperty("buy_price") BigDecimal buyPrice,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("price_date") String priceDate,
        @JsonProperty("pnl") Long pnl,
        @JsonProperty("return_pct") BigDecimal returnPct) {

    public static HoldingDto from(HoldingView v) {
        return new HoldingDto(
                v.id(),
                v.code(),
                v.name(),
                v.market() == null ? null : v.market().name(),
                v.lots(),
                v.buyPrice(),
                v.currentPrice(),
                v.priceDate() == null ? null : v.priceDate().toString(), // ISO：YYYY-MM-DD
                v.pnl(),
                v.returnPct());
    }
}
