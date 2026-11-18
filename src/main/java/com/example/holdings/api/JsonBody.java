package com.example.holdings.api;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.Side;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;

/**
 * 請求內容的讀取與型別檢查。型別不對一律 400，不做悄悄轉型（例如 1.5 張不會變成 1 張）。
 */
final class JsonBody {

    private JsonBody() {
    }

    static JsonNode parse(ObjectMapper mapper, String body) {
        JsonNode json;
        try {
            // 小數保留成精確的 BigDecimal，不經過 double
            json = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        } catch (Exception e) {
            throw invalid("請求內容不是合法的 JSON");
        }
        if (json == null || !json.isObject()) {
            throw invalid("請求內容必須是 JSON 物件");
        }
        return json;
    }

    static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            throw invalid(field + " 必填");
        }
        if (!node.isTextual()) {
            throw invalid(field + " 必須是字串");
        }
        return node.asText();
    }

    /** 整數欄位；message 是給使用者看的訊息（1.0 的 lots 訊息與 2.0 不同）。 */
    static int integer(JsonNode json, String field, String message) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            throw invalid(field + " 必填");
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(message);
        }
        return node.intValue();
    }

    static BigDecimal price(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            throw invalid(field + " 必填");
        }
        if (!node.isNumber()) {
            throw invalid(field + " 必須是數字");
        }
        return node.decimalValue();
    }

    static LocalDate date(JsonNode json, String field) {
        String raw = text(json, field);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw invalid(field + " 格式應為 YYYY-MM-DD");
        }
    }

    static Side side(JsonNode json) {
        String raw = text(json, "side");
        try {
            return Side.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw invalid("side 只能是 BUY 或 SELL");
        }
    }

    static HoldingException invalid(String message) {
        return new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
