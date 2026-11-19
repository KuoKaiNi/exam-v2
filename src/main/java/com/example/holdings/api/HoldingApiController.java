package com.example.holdings.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.holdings.holding.Holding;
import com.example.holdings.holding.HoldingException;
import com.example.holdings.holding.HoldingInput;
import com.example.holdings.holding.HoldingQueryService;
import com.example.holdings.holding.HoldingService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 給 Android App 1.0 用的 API，完全照《App 1.0 API 規格》。App 已發布不會再改，所以這裡的欄位、型別、狀態碼都不能動。
 */
@RestController
@RequestMapping("/api/v1")
public class HoldingApiController {

    static final String JSON_UTF8 = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

    private final HoldingService holdingService;
    private final HoldingQueryService queryService;

    public HoldingApiController(HoldingService holdingService, HoldingQueryService queryService) {
        this.holdingService = holdingService;
        this.queryService = queryService;
    }

    @GetMapping("/holdings")
    public List<HoldingDto> list() {
        return queryService.list().stream().map(HoldingDto::from).toList();
    }

    @GetMapping("/holdings/{id}")
    public HoldingDto get(@PathVariable String id) {
        return HoldingDto.from(queryService.get(parseId(id)));
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingDto> create(@RequestBody(required = false) String body) {
        JsonNode json = RequestBodies.parse(body);
        Holding h = holdingService.create(
                RequestBodies.field(json, "code"),
                RequestBodies.field(json, "lots"),
                RequestBodies.field(json, "buy_price"));
        return ResponseEntity.status(HttpStatus.CREATED).body(HoldingDto.from(queryService.toView(h)));
    }

    @PutMapping("/holdings/{id}")
    public HoldingDto update(@PathVariable String id, @RequestBody(required = false) String body) {
        long holdingId = parseId(id);
        Holding existing = holdingService.get(holdingId); // 先確認存在：不存在回 404
        JsonNode json = RequestBodies.parse(body);
        String code = RequestBodies.field(json, "code");
        if (code != null && !code.isBlank() && !HoldingInput.parseCode(code).equals(existing.getCode())) {
            throw HoldingException.validation("股票代號不能修改");
        }
        Holding h = holdingService.update(holdingId,
                RequestBodies.field(json, "lots"),
                RequestBodies.field(json, "buy_price"));
        return HoldingDto.from(queryService.toView(h));
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        holdingService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        return SummaryDto.from(queryService.summary());
    }

    /** id 不是數字（例如 /holdings/abc）就當作找不到。 */
    private static long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new HoldingException(HoldingException.Kind.NOT_FOUND, "找不到編號 " + raw + " 的持股");
        }
    }
}
