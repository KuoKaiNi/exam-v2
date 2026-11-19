package com.example.holdings.api.v2;

import java.math.BigDecimal;
import java.util.List;

import com.example.holdings.holding.PortfolioSummary;
import com.example.holdings.holding.PositionView;
import com.example.holdings.holding.TradeView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** App 2.0 API 的回應格式（見 API.md）。欄位小寫加底線，取不到的值是 null，欄位一定會出現。 */
public final class V2Dtos {

    private V2Dtos() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HoldingDto(
            @JsonProperty("id") long id,
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("market") String market,
            @JsonProperty("shares") long shares,
            @JsonProperty("avg_cost") BigDecimal avgCost,
            @JsonProperty("total_cost") long totalCost,
            @JsonProperty("current_price") BigDecimal currentPrice,
            @JsonProperty("price_date") String priceDate,
            @JsonProperty("market_value") Long marketValue,
            @JsonProperty("unrealized_pnl") Long unrealizedPnl,
            @JsonProperty("unrealized_return_pct") BigDecimal unrealizedReturnPct,
            @JsonProperty("realized_pnl") long realizedPnl,
            @JsonProperty("trades") @JsonInclude(JsonInclude.Include.NON_NULL) List<TradeDto> trades) {

        public static HoldingDto of(PositionView v, boolean withTrades) {
            return new HoldingDto(v.id(), v.code(), v.name(), v.market() == null ? null : v.market().name(),
                    v.shares(), v.avgCost(), v.cost(), v.currentPrice(),
                    v.priceDate() == null ? null : v.priceDate().toString(),
                    v.marketValue(), v.unrealizedPnl(), v.unrealizedReturnPct(), v.realizedPnl(),
                    withTrades ? v.trades().stream().map(TradeDto::of).toList() : null);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TradeDto(
            @JsonProperty("id") long id,
            @JsonProperty("holding_id") long holdingId,
            @JsonProperty("side") String side,
            @JsonProperty("date") String date,
            @JsonProperty("date_estimated") boolean dateEstimated,
            @JsonProperty("shares") long shares,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("amount") long amount,
            @JsonProperty("fee") long fee,
            @JsonProperty("tax") long tax,
            @JsonProperty("cost_basis") Long costBasis,
            @JsonProperty("realized_pnl") Long realizedPnl,
            @JsonProperty("shares_after") long sharesAfter,
            @JsonProperty("version") long version) {

        public static TradeDto of(TradeView t) {
            return new TradeDto(t.id(), t.holdingId(), t.side().name(), t.date().toString(), t.dateEstimated(),
                    t.shares(), t.price(), t.amount(), t.fee(), t.tax(), t.costBasis(), t.realizedPnl(),
                    t.sharesAfter(), t.version());
        }
    }

    /** 新增或修改交易後的回應：這筆交易＋更新後的部位。 */
    public record TradeResultDto(@JsonProperty("trade") TradeDto trade, @JsonProperty("holding") HoldingDto holding) {
    }

    public record SummaryDto(
            @JsonProperty("total_cost") long totalCost,
            @JsonProperty("market_value") long marketValue,
            @JsonProperty("unrealized_pnl") long unrealizedPnl,
            @JsonProperty("unrealized_return_pct") BigDecimal unrealizedReturnPct,
            @JsonProperty("realized_pnl") long realizedPnl,
            @JsonProperty("quote_missing") int quoteMissing,
            @JsonProperty("open_positions") int openPositions) {

        public static SummaryDto of(PortfolioSummary s) {
            return new SummaryDto(s.totalCost(), s.marketValue(), s.unrealizedPnl(), s.unrealizedReturnPct(),
                    s.realizedPnl(), s.quoteMissing(), s.openPositions());
        }
    }
}
