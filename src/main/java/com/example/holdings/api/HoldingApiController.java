package com.example.holdings.api;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRules;
import com.example.holdings.domain.PnlCalculator;
import com.example.holdings.service.PositionQueryService;
import com.example.holdings.service.PositionService;
import com.example.holdings.service.PositionView;
import com.example.holdings.service.Totals;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 1.0 使用的 API，規格見《App 1.0 API 規格》，路徑與欄位不變。
 * 1.0 只看「整張」的部位（股數是 1000 的倍數）；零股或部分賣出後不是整張的部位不出現在 1.0。
 * 1.0 沒有冪等機制，重送 POST 會得到 409 DUPLICATE_CODE。
 */
@RestController
@RequestMapping("/api/v1")
public class HoldingApiController {

    private final PositionService positions;
    private final PositionQueryService queries;
    private final ObjectMapper mapper;

    public HoldingApiController(PositionService positions, PositionQueryService queries, ObjectMapper mapper) {
        this.positions = positions;
        this.queries = queries;
        this.mapper = mapper;
    }

    @GetMapping("/holdings")
    public List<HoldingDto> list() {
        return queries.list().stream().filter(PositionView::wholeLotsForV1).map(HoldingApiController::toDto).toList();
    }

    @GetMapping("/holdings/{id}")
    public HoldingDto get(@PathVariable Long id) {
        return toDto(visible(queries.get(id)));
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingDto> create(@RequestBody String body) {
        JsonNode json = JsonBody.parse(mapper, body);
        String code = JsonBody.text(json, "code");
        int lots = lots(json);
        BigDecimal price = JsonBody.price(json, "buy_price");
        Long id = positions.createPosition(code, null, lots * PnlCalculator.SHARES_PER_LOT, price);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(queries.get(id)));
    }

    @PutMapping("/holdings/{id}")
    public HoldingDto update(@PathVariable Long id, @RequestBody String body) {
        visible(queries.get(id));
        JsonNode json = JsonBody.parse(mapper, body);
        // 代號不能改，請求裡的 code 忽略
        int lots = lots(json);
        BigDecimal price = JsonBody.price(json, "buy_price");
        positions.updateSingleBuy(id, lots * PnlCalculator.SHARES_PER_LOT, price);
        return toDto(queries.get(id));
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        positions.deletePosition(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        List<PositionView> whole = queries.list().stream().filter(PositionView::wholeLotsForV1).toList();
        Totals t = Totals.of(whole);
        return new SummaryDto(t.costBasis(), t.marketValue(), t.unrealizedPnl(), t.unrealizedReturnPct(),
                t.quoteMissing());
    }

    private static int lots(JsonNode json) {
        int lots = JsonBody.integer(json, "lots", "張數必須是 1 到 999 的整數");
        HoldingRules.checkLots(lots);
        return lots;
    }

    private static PositionView visible(PositionView v) {
        if (!v.wholeLotsForV1()) {
            throw new HoldingException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "這檔持股不是整張（或已部分賣出），請用 2.0 版查看");
        }
        return v;
    }

    /** 1.0 的 DTO：lots 為整張數，buy_price 為成交均價，pnl 與 return_pct 為未實現損益。 */
    static HoldingDto toDto(PositionView v) {
        return new HoldingDto(
                v.id(), v.code(), v.name(), v.market().name(), v.shares() / 1000, v.avgPrice(),
                v.currentPrice(),
                v.priceDate() == null ? null : v.priceDate().toString(),
                v.unrealizedPnl(), v.unrealizedReturnPct());
    }
}
