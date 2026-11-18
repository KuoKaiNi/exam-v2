package com.example.holdings.api;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.service.HoldingQueryService;
import com.example.holdings.service.HoldingService;
import com.example.holdings.service.HoldingView;
import com.example.holdings.service.Summary;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
 * App 1.0 使用的 API，規格見附件《App 1.0 API 規格》，欄位與狀態碼不能改。
 * 請求內容先用 JsonNode 讀，這樣「張數 1.5」「張數 \"1\"」這類型別錯誤會回 400，不會被悄悄轉型。
 */
@RestController
@RequestMapping("/api/v1")
public class HoldingApiController {

    private final HoldingService service;
    private final HoldingQueryService queryService;
    private final ObjectMapper mapper;

    public HoldingApiController(HoldingService service, HoldingQueryService queryService, ObjectMapper mapper) {
        this.service = service;
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @GetMapping("/holdings")
    public List<HoldingDto> list() {
        return queryService.list().stream().map(HoldingApiController::toDto).toList();
    }

    @GetMapping("/holdings/{id}")
    public HoldingDto get(@PathVariable Long id) {
        return toDto(queryService.get(id));
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingDto> create(@RequestBody String body) {
        JsonNode json = readBody(body);
        String code = requireText(json, "code");
        int lots = requireLots(json);
        BigDecimal buyPrice = requirePrice(json);
        var created = service.create(code, lots, buyPrice);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(queryService.get(created.getId())));
    }

    @PutMapping("/holdings/{id}")
    public HoldingDto update(@PathVariable Long id, @RequestBody String body) {
        JsonNode json = readBody(body);
        // 代號不能改，請求裡的 code 忽略
        int lots = requireLots(json);
        BigDecimal buyPrice = requirePrice(json);
        service.update(id, lots, buyPrice);
        return toDto(queryService.get(id));
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        Summary s = Summary.of(queryService.list());
        return new SummaryDto(s.totalCost(), s.marketValue(), s.pnl(), s.returnPct(), s.quoteMissing());
    }

    private JsonNode readBody(String body) {
        try {
            // 讓小數保留成精確的 BigDecimal，不經過 double
            JsonNode json = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
            if (json == null || !json.isObject()) {
                throw invalid("請求內容必須是 JSON 物件");
            }
            return json;
        } catch (HoldingException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("請求內容不是合法的 JSON");
        }
    }

    private static String requireText(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            throw invalid(field + " 必填");
        }
        if (!node.isTextual()) {
            throw invalid(field + " 必須是字串");
        }
        return node.asText();
    }

    private static int requireLots(JsonNode json) {
        JsonNode node = json.get("lots");
        if (node == null || node.isNull()) {
            throw invalid("lots 必填");
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("張數必須是 1 到 999 的整數");
        }
        return node.intValue();
    }

    private static BigDecimal requirePrice(JsonNode json) {
        JsonNode node = json.get("buy_price");
        if (node == null || node.isNull()) {
            throw invalid("buy_price 必填");
        }
        if (!node.isNumber()) {
            throw invalid("買進價必須是數字");
        }
        return node.decimalValue();
    }

    private static HoldingException invalid(String message) {
        return new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    static HoldingDto toDto(HoldingView h) {
        return new HoldingDto(
                h.id(), h.code(), h.name(), h.market().name(), h.lots(), h.buyPrice(),
                h.currentPrice(),
                h.priceDate() == null ? null : h.priceDate().toString(),
                h.pnl(), h.returnPct());
    }
}
