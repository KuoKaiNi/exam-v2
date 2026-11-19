package com.example.holdings.api;

import com.example.holdings.holding.HoldingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * 自己解析請求內容：App 1.0 不在手機上檢查輸入，填什麼就送什麼，所以 lots 可能是 1.5、"abc"、null。
 * 先把每個欄位取成字串，再交給跟網頁共用的 HoldingInput 檢查，錯了一律回 400 VALIDATION_ERROR。
 */
final class RequestBodies {

    private static final ObjectReader READER = new ObjectMapper()
            .reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private RequestBodies() {
    }

    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw HoldingException.validation("請求內容是空的，要送 JSON 物件");
        }
        try {
            JsonNode node = READER.readTree(body);
            if (node == null || !node.isObject()) {
                throw HoldingException.validation("請求內容要是 JSON 物件");
            }
            return node;
        } catch (HoldingException e) {
            throw e;
        } catch (Exception e) {
            throw HoldingException.validation("請求內容不是正確的 JSON");
        }
    }

    /** 欄位轉成字串；不存在或 null 回 null（由檢查規則報「必填」）。數字保留原樣，例如 1.5 → "1.5"。 */
    static String field(JsonNode obj, String name) {
        JsonNode v = obj.get(name);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isIntegralNumber()) {
            return v.bigIntegerValue().toString();
        }
        if (v.isNumber()) {
            return v.decimalValue().toPlainString();
        }
        return v.toString(); // true、物件、陣列：交給檢查規則報格式錯誤
    }
}
