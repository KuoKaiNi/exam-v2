package com.example.holdings.api;

import java.math.BigDecimal;

import com.example.holdings.holding.PositionView;
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

    /**
     * 2.0 的部位轉成 App 1.0 看得懂的樣子：
     * lots＝股數 ÷ 1000 無條件捨去（App 1.0 規定是整數，零股表達不了）；buy_price＝平均買進價（不含手續費）；
     * pnl、return_pct＝整個部位（含零股）的未實現損益。
     */
    public static HoldingDto from(PositionView v) {
        return new HoldingDto(
                v.id(),
                v.code(),
                v.name(),
                v.market() == null ? null : v.market().name(),
                (int) (v.shares() / 1000),
                v.avgPrice(),
                v.currentPrice(),
                v.priceDate() == null ? null : v.priceDate().toString(), // ISO：YYYY-MM-DD
                v.unrealizedPnl(),
                v.unrealizedReturnPct());
    }
}
