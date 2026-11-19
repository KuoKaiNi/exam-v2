package com.example.holdings.api.v2;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.holdings.api.RequestBodies;
import com.example.holdings.holding.HoldingException;
import com.example.holdings.holding.HoldingInput;
import com.example.holdings.holding.PortfolioQueryService;
import com.example.holdings.holding.PortfolioService;
import com.example.holdings.holding.Trade;
import com.example.holdings.idempotency.IdempotencyService;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * App 2.0 用的 API（規格見 repo 根目錄 API.md）。所有寫入都要帶 Idempotency-Key 標頭。
 */
@RestController
@RequestMapping("/api/v2")
public class V2ApiController {

    private final PortfolioService portfolio;
    private final PortfolioQueryService query;
    private final IdempotencyService idempotency;

    public V2ApiController(PortfolioService portfolio, PortfolioQueryService query, IdempotencyService idempotency) {
        this.portfolio = portfolio;
        this.query = query;
        this.idempotency = idempotency;
    }

    @GetMapping("/holdings")
    public List<V2Dtos.HoldingDto> holdings(@RequestParam(name = "include_closed", required = false) String includeClosed) {
        boolean all = "true".equalsIgnoreCase(includeClosed) || "1".equals(includeClosed);
        return query.positions(all).stream().map(v -> V2Dtos.HoldingDto.of(v, false)).toList();
    }

    @GetMapping("/holdings/{id}")
    public V2Dtos.HoldingDto holding(@PathVariable String id) {
        return V2Dtos.HoldingDto.of(query.position(parseId(id, "持股")), true);
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<String> deleteHolding(@PathVariable String id,
                                                @RequestHeader(name = IdempotencyService.HEADER, required = false) String key,
                                                HttpServletRequest req) {
        return idempotency.execute(key, fingerprint(req, null), () -> {
            portfolio.deleteHolding(parseId(id, "持股"));
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/trades/{id}")
    public V2Dtos.TradeDto trade(@PathVariable String id) {
        return V2Dtos.TradeDto.of(query.trade(parseId(id, "交易")));
    }

    @PostMapping("/trades")
    public ResponseEntity<String> addTrade(@RequestBody(required = false) String body,
                                           @RequestHeader(name = IdempotencyService.HEADER, required = false) String key,
                                           HttpServletRequest req) {
        return idempotency.execute(key, fingerprint(req, body), () -> {
            JsonNode json = RequestBodies.parse(body);
            Trade t = portfolio.addTrade(
                    RequestBodies.field(json, "code"),
                    RequestBodies.field(json, "side"),
                    RequestBodies.field(json, "date"),
                    RequestBodies.field(json, "shares"),
                    RequestBodies.field(json, "price"));
            return ResponseEntity.status(HttpStatus.CREATED).body(result(t.getId()));
        });
    }

    @PutMapping("/trades/{id}")
    public ResponseEntity<String> updateTrade(@PathVariable String id, @RequestBody(required = false) String body,
                                              @RequestHeader(name = IdempotencyService.HEADER, required = false) String key,
                                              HttpServletRequest req) {
        return idempotency.execute(key, fingerprint(req, body), () -> {
            long tradeId = parseId(id, "交易");
            JsonNode json = RequestBodies.parse(body);
            String side = RequestBodies.field(json, "side");
            if (side != null && HoldingInput.parseSide(side) != query.trade(tradeId).side()) {
                throw HoldingException.validation("買賣方向不能修改，請刪除後重新新增");
            }
            Trade t = portfolio.updateTrade(tradeId,
                    RequestBodies.field(json, "date"),
                    RequestBodies.field(json, "shares"),
                    RequestBodies.field(json, "price"),
                    HoldingInput.parseVersion(RequestBodies.field(json, "version")));
            return ResponseEntity.ok(result(t.getId()));
        });
    }

    @DeleteMapping("/trades/{id}")
    public ResponseEntity<String> deleteTrade(@PathVariable String id,
                                              @RequestParam(name = "version", required = false) String version,
                                              @RequestHeader(name = IdempotencyService.HEADER, required = false) String key,
                                              HttpServletRequest req) {
        return idempotency.execute(key, fingerprint(req, null), () -> {
            portfolio.deleteTrade(parseId(id, "交易"), HoldingInput.parseVersion(version));
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/summary")
    public V2Dtos.SummaryDto summary() {
        return V2Dtos.SummaryDto.of(query.summary());
    }

    private V2Dtos.TradeResultDto result(long tradeId) {
        var trade = query.trade(tradeId);
        return new V2Dtos.TradeResultDto(V2Dtos.TradeDto.of(trade),
                V2Dtos.HoldingDto.of(query.position(trade.holdingId()), false));
    }

    private static String fingerprint(HttpServletRequest req, String body) {
        String path = req.getRequestURI() + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
        return IdempotencyService.fingerprint(req.getMethod(), path, body);
    }

    private static long parseId(String raw, String what) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new HoldingException(HoldingException.Kind.NOT_FOUND, "找不到編號 " + raw + " 的" + what);
        }
    }
}
