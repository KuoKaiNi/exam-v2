package com.example.holdings.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.holdings.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 照《App 1.0 API 規格》逐條驗：欄位名稱、型別、狀態碼、錯誤格式。行情來自本機假官方伺服器。
 */
class HoldingApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    private ResponseEntity<String> call(HttpMethod method, String path, String json) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(json, h), String.class);
    }

    private JsonNode body(ResponseEntity<String> r) throws Exception {
        return mapper.readTree(r.getBody());
    }

    private void assertError(ResponseEntity<String> r, int status, String code) throws Exception {
        assertThat(r.getStatusCode().value()).isEqualTo(status);
        JsonNode err = body(r).path("error");
        assertThat(err.path("code").asText()).isEqualTo(code);
        assertThat(err.path("message").isTextual()).isTrue();
        assertThat(err.path("message").asText()).isNotBlank();
    }

    @Test
    void emptyListIsEmptyArray() throws Exception {
        ResponseEntity<String> r = call(HttpMethod.GET, "/api/v1/holdings", null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).isEqualTo("[]");
        assertThat(r.getHeaders().getContentType().toString()).contains("application/json").containsIgnoringCase("utf-8");
    }

    @Test
    void createReturns201WithExactFieldsAndTypes() throws Exception {
        ResponseEntity<String> r = call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        JsonNode h = body(r);
        assertThat(h.path("id").isIntegralNumber()).isTrue();
        assertThat(h.path("code").asText()).isEqualTo("2330");
        assertThat(h.path("name").asText()).isEqualTo("台積電");
        assertThat(h.path("market").asText()).isEqualTo("TWSE");
        assertThat(h.path("lots").isInt()).isTrue();
        assertThat(h.path("lots").asInt()).isEqualTo(1);
        assertThat(h.path("buy_price").isNumber()).isTrue();
        assertThat(h.path("buy_price").decimalValue()).isEqualByComparingTo("1000");
        assertThat(h.path("current_price").decimalValue()).isEqualByComparingTo("1100");
        assertThat(h.path("price_date").asText()).isEqualTo("2026-11-12");
        assertThat(h.path("pnl").isIntegralNumber()).isTrue();
        assertThat(h.path("pnl").asLong()).isEqualTo(93708);
        assertThat(h.path("return_pct").isNumber()).isTrue();
        assertThat(h.path("return_pct").decimalValue()).isEqualByComparingTo("9.36");
        assertThat(h.size()).isEqualTo(10);
    }

    @Test
    void otcMarketIsTpex() throws Exception {
        JsonNode h = body(call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"6488\",\"lots\":2,\"buy_price\":600}"));
        assertThat(h.path("market").asText()).isEqualTo("TPEX");
        assertThat(h.path("name").asText()).isEqualTo("環球晶");
    }

    @Test
    void missingQuoteGivesNullsButKeepsFields() throws Exception {
        long id = body(call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}")).path("id").asLong();
        quoteRepository.findById("2330").ifPresent(q -> {
            quoteRepository.delete(q);
            quoteRepository.save(new com.example.holdings.quote.StockQuote("2330", com.example.holdings.quote.Market.TWSE, "台積電"));
        });
        JsonNode h = body(call(HttpMethod.GET, "/api/v1/holdings/" + id, null));
        for (String f : new String[] {"current_price", "price_date", "pnl", "return_pct"}) {
            assertThat(h.has(f)).as(f + " 欄位要在").isTrue();
            assertThat(h.get(f).isNull()).as(f + " 為 null").isTrue();
        }
        JsonNode s = body(call(HttpMethod.GET, "/api/v1/summary", null));
        assertThat(s.path("quote_missing").asInt()).isEqualTo(1);
        assertThat(s.path("total_cost").asLong()).isEqualTo(1_001_425);
        assertThat(s.path("market_value").asLong()).isZero();
        assertThat(s.path("pnl").asLong()).isZero();
    }

    @Test
    void summaryMatchesSpecExample() throws Exception {
        call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"0050\",\"lots\":2,\"buy_price\":150}");
        ResponseEntity<String> r = call(HttpMethod.GET, "/api/v1/summary", null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode s = body(r);
        assertThat(s.path("total_cost").isIntegralNumber()).isTrue();
        assertThat(s.path("total_cost").asLong()).isEqualTo(1_301_852);
        assertThat(s.path("market_value").asLong()).isEqualTo(1_391_000);
        assertThat(s.path("pnl").asLong()).isEqualTo(83_576);
        assertThat(s.path("return_pct").decimalValue()).isEqualByComparingTo("6.42");
        assertThat(s.path("quote_missing").asInt()).isZero();
        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings", null)).size()).isEqualTo(2);
    }

    @Test
    void getUpdateDelete() throws Exception {
        long id = body(call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}")).path("id").asLong();

        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings/" + id, null)).path("code").asText()).isEqualTo("2330");

        ResponseEntity<String> put = call(HttpMethod.PUT, "/api/v1/holdings/" + id, "{\"lots\":2,\"buy_price\":980.5}");
        assertThat(put.getStatusCode().value()).isEqualTo(200);
        assertThat(body(put).path("lots").asInt()).isEqualTo(2);
        assertThat(body(put).path("buy_price").decimalValue()).isEqualByComparingTo("980.5");
        assertThat(body(put).path("id").asLong()).isEqualTo(id);

        ResponseEntity<String> del = call(HttpMethod.DELETE, "/api/v1/holdings/" + id, null);
        assertThat(del.getStatusCode().value()).isEqualTo(204);
        assertThat(del.getBody()).isNull();

        assertError(call(HttpMethod.GET, "/api/v1/holdings/" + id, null), 404, "NOT_FOUND");
        assertError(call(HttpMethod.DELETE, "/api/v1/holdings/" + id, null), 404, "NOT_FOUND");
        assertError(call(HttpMethod.PUT, "/api/v1/holdings/" + id, "{\"lots\":2,\"buy_price\":980.5}"), 404, "NOT_FOUND");
        assertError(call(HttpMethod.GET, "/api/v1/holdings/abc", null), 404, "NOT_FOUND");
    }

    @Test
    void duplicateIs409AndNotSaved() throws Exception {
        call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        ResponseEntity<String> r = call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":3,\"buy_price\":900}");
        assertError(r, 409, "DUPLICATE_CODE");
        assertThat(body(r).path("error").path("message").asText()).isEqualTo("2330 已在持股中");
        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings", null)).size()).isEqualTo(1);
    }

    @Test
    void unknownStockIs404StockNotFound() throws Exception {
        assertError(call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"9999\",\"lots\":1,\"buy_price\":10}"), 404, "STOCK_NOT_FOUND");
        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings", null)).size()).isZero();
    }

    @Test
    void validationErrorsAre400() throws Exception {
        String[] bad = {
                "{\"code\":\"2330\",\"lots\":1.5,\"buy_price\":1000}",
                "{\"code\":\"2330\",\"lots\":0,\"buy_price\":1000}",
                "{\"code\":\"2330\",\"lots\":1000,\"buy_price\":1000}",
                "{\"code\":\"2330\",\"lots\":\"abc\",\"buy_price\":1000}",
                "{\"code\":\"2330\",\"lots\":1,\"buy_price\":0}",
                "{\"code\":\"2330\",\"lots\":1,\"buy_price\":-1}",
                "{\"code\":\"2330\",\"lots\":1,\"buy_price\":10.123}",
                "{\"code\":\"\",\"lots\":1,\"buy_price\":10}",
                "{\"lots\":1,\"buy_price\":10}",
                "{\"code\":\"2330\",\"buy_price\":10}",
                "{\"code\":\"2330\",\"lots\":null,\"buy_price\":10}",
                "not json",
                "[]",
                ""
        };
        for (String b : bad) {
            assertError(call(HttpMethod.POST, "/api/v1/holdings", b), 400, "VALIDATION_ERROR");
        }
        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings", null)).size()).isZero();
        assertThat(FAKE.requests()).as("格式錯就不查官方").isEmpty();
    }

    @Test
    void numericStringsAreAccepted() throws Exception {
        ResponseEntity<String> r = call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":\"1\",\"buy_price\":\"1000.00\"}");
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(body(r).path("lots").isInt()).isTrue();
    }

    @Test
    void updateValidationAndCodeCannotChange() throws Exception {
        long id = body(call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}")).path("id").asLong();
        assertError(call(HttpMethod.PUT, "/api/v1/holdings/" + id, "{\"lots\":1.5,\"buy_price\":1000}"), 400, "VALIDATION_ERROR");
        assertError(call(HttpMethod.PUT, "/api/v1/holdings/" + id, "{\"code\":\"2317\",\"lots\":1,\"buy_price\":1000}"), 400, "VALIDATION_ERROR");
        assertThat(call(HttpMethod.PUT, "/api/v1/holdings/" + id, "{\"code\":\"2330\",\"lots\":3,\"buy_price\":1000}").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void sourceDownOnCreateIsErrorFormat() throws Exception {
        FAKE.twseDown.set(true);
        FAKE.tpexDown.set(true);
        ResponseEntity<String> r = call(HttpMethod.POST, "/api/v1/holdings", "{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        assertError(r, 503, "QUOTE_UNAVAILABLE");
    }

    @Test
    void unknownApiPathIsErrorFormat() throws Exception {
        assertError(call(HttpMethod.GET, "/api/v1/nothing", null), 404, "NOT_FOUND");
    }
}
