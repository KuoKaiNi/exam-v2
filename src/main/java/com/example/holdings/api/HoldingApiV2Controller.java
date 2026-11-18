package com.example.holdings.api;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRules;
import com.example.holdings.domain.Side;
import com.example.holdings.service.IdempotentWriter;
import com.example.holdings.service.IdempotentWriter.Stored;
import com.example.holdings.service.PositionQueryService;
import com.example.holdings.service.PositionService;
import com.example.holdings.service.PositionView;
import com.example.holdings.service.TradeView;
import com.example.holdings.service.Totals;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2.0 API（/api/v2）。所有寫入（POST、DELETE）必須帶 Idempotency-Key 標頭；
 * 手機網路不穩、10 秒沒回應就會重送，同一個 key 重送會拿到第一次的結果。
 */
@RestController
@RequestMapping("/api/v2")
public class HoldingApiV2Controller {

    static final String KEY_HEADER = "Idempotency-Key";

    private final PositionService positions;
    private final PositionQueryService queries;
    private final IdempotentWriter idempotent;
    private final ObjectMapper mapper;

    public HoldingApiV2Controller(PositionService positions, PositionQueryService queries,
                                  IdempotentWriter idempotent, ObjectMapper mapper) {
        this.positions = positions;
        this.queries = queries;
        this.idempotent = idempotent;
        this.mapper = mapper;
    }

    @GetMapping("/holdings")
    public List<PositionDtoV2> list() {
        return queries.list().stream().map(p -> toDto(p)).toList();
    }

    @GetMapping("/holdings/{id}")
    public PositionDtoV2 get(@PathVariable Long id) {
        return toDto(queries.get(id));
    }

    @PostMapping("/holdings")
    public ResponseEntity<String> create(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                         @RequestBody String body, HttpServletRequest request) {
        Stored s = idempotent.run(requireKey(key), fingerprint(request, body), () -> {
            JsonNode json = JsonBody.parse(mapper, body);
            String code = JsonBody.text(json, "code");
            var date = JsonBody.date(json, "trade_date");
            int shares = JsonBody.integer(json, "shares", "股數必須是 1 到 999,999 的整數");
            HoldingRules.checkShares(shares);
            var price = JsonBody.price(json, "price");
            Long id = positions.createPosition(code, date, shares, price);
            return new Stored(HttpStatus.CREATED.value(), json(toDto(queries.get(id))));
        });
        return respond(s);
    }

    @PostMapping("/holdings/{id}/trades")
    public ResponseEntity<String> addTrade(@PathVariable Long id,
                                           @RequestHeader(value = KEY_HEADER, required = false) String key,
                                           @RequestBody String body, HttpServletRequest request) {
        Stored s = idempotent.run(requireKey(key), fingerprint(request, body), () -> {
            JsonNode json = JsonBody.parse(mapper, body);
            Side side = JsonBody.side(json);
            var date = JsonBody.date(json, "trade_date");
            int shares = JsonBody.integer(json, "shares", "股數必須是 1 到 999,999 的整數");
            var price = JsonBody.price(json, "price");
            positions.addTrade(id, side, date, shares, price);
            return new Stored(HttpStatus.CREATED.value(), json(toDto(queries.get(id))));
        });
        return respond(s);
    }

    @DeleteMapping("/holdings/{id}/trades/{tradeId}")
    public ResponseEntity<String> deleteTrade(@PathVariable Long id, @PathVariable Long tradeId,
                                              @RequestHeader(value = KEY_HEADER, required = false) String key,
                                              HttpServletRequest request) {
        Stored s = idempotent.run(requireKey(key), fingerprint(request, ""), () -> {
            positions.deleteTrade(id, tradeId);
            return new Stored(HttpStatus.NO_CONTENT.value(), "");
        });
        return respond(s);
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id,
                                         @RequestHeader(value = KEY_HEADER, required = false) String key,
                                         HttpServletRequest request) {
        Stored s = idempotent.run(requireKey(key), fingerprint(request, ""), () -> {
            positions.deletePosition(id);
            return new Stored(HttpStatus.NO_CONTENT.value(), "");
        });
        return respond(s);
    }

    @GetMapping("/summary")
    public TotalsDtoV2 summary() {
        Totals t = Totals.of(queries.list());
        return new TotalsDtoV2(t.costBasis(), t.marketValue(), t.unrealizedPnl(), t.unrealizedReturnPct(),
                t.realizedPnl(), t.quoteMissing());
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new HoldingException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "缺少 " + KEY_HEADER + " 標頭");
        }
        if (key.length() > 100) {
            throw new HoldingException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    KEY_HEADER + " 不能超過 100 個字元");
        }
        return key.trim();
    }

    /** 同一個 key 只能用在同一個請求（方法、路徑、內容都相同）。 */
    private static String fingerprint(HttpServletRequest request, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = request.getMethod() + " " + request.getRequestURI() + "\n" + body;
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> respond(Stored s) {
        if (s.status() == HttpStatus.NO_CONTENT.value()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(s.status()).contentType(MediaType.APPLICATION_JSON).body(s.body());
    }

    private String json(Object dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static PositionDtoV2 toDto(PositionView v) {
        return new PositionDtoV2(
                v.id(), v.code(), v.name(), v.market().name(), v.shares(), v.avgPrice(), v.avgCost(),
                v.costBasis(), v.realizedPnl(), v.currentPrice(),
                v.priceDate() == null ? null : v.priceDate().toString(),
                v.marketValue(), v.unrealizedPnl(), v.unrealizedReturnPct(),
                v.trades().stream().map(t -> toDto(t)).toList());
    }

    static TradeDtoV2 toDto(TradeView t) {
        return new TradeDtoV2(t.id(), t.side().name(),
                t.tradeDate() == null ? null : t.tradeDate().toString(),
                t.shares(), t.price(), t.amount(), t.fee(), t.tax(), t.realizedPnl());
    }
}
