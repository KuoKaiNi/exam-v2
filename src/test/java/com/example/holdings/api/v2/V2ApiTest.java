package com.example.holdings.api.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

/** App 2.0 API（API.md）的整合測試。行情來自本機假官方伺服器，時鐘固定 2026-11-12 15:00 台北。 */
class V2ApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    private ResponseEntity<String> call(HttpMethod method, String path, String json, String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            h.set("Idempotency-Key", key);
        }
        return rest.exchange(path, method, new HttpEntity<>(json, h), String.class);
    }

    private ResponseEntity<String> post(String json) {
        return call(HttpMethod.POST, "/api/v2/trades", json, UUID.randomUUID().toString());
    }

    private JsonNode body(ResponseEntity<String> r) throws Exception {
        return mapper.readTree(r.getBody());
    }

    private static String buy(String code, String date, long shares, String price) {
        return "{\"code\":\"" + code + "\",\"side\":\"BUY\",\"date\":\"" + date + "\",\"shares\":" + shares + ",\"price\":" + price + "}";
    }

    private static String sell(String code, String date, long shares, String price) {
        return buy(code, date, shares, price).replace("BUY", "SELL");
    }

    @Test
    void buyTwiceSellPartAndReadBack() throws Exception {
        ResponseEntity<String> r1 = post(buy("2330", "2026-11-02", 1000, "1000"));
        assertThat(r1.getStatusCode().value()).isEqualTo(201);
        JsonNode t1 = body(r1);
        long holdingId = t1.path("holding").path("id").asLong();
        assertThat(t1.path("trade").path("side").asText()).isEqualTo("BUY");
        assertThat(t1.path("trade").path("fee").asLong()).isEqualTo(1425);
        assertThat(t1.path("holding").path("shares").asLong()).isEqualTo(1000);

        post(buy("2330", "2026-11-05", 500, "1100"));
        ResponseEntity<String> r3 = post(sell("2330", "2026-11-10", 300, "1090"));
        JsonNode s = body(r3);
        assertThat(s.path("trade").path("realized_pnl").isIntegralNumber()).isTrue();
        assertThat(s.path("trade").path("cost_basis").asLong()).isEqualTo(310_442); // 1,552,208 × 300/1,500 = 310,441.6
        assertThat(s.path("holding").path("shares").asLong()).isEqualTo(1200);
        assertThat(s.path("holding").path("avg_cost").decimalValue()).isEqualByComparingTo("1034.81");

        JsonNode detail = body(call(HttpMethod.GET, "/api/v2/holdings/" + holdingId, null, null));
        assertThat(detail.path("trades").size()).isEqualTo(3);
        assertThat(detail.path("trades").get(0).path("side").asText()).as("新的在前").isEqualTo("SELL");
        assertThat(detail.path("total_cost").asLong()).isEqualTo(1_241_766); // 1,552,208 − 310,441.6
        assertThat(detail.path("unrealized_pnl").isIntegralNumber()).isTrue();
        assertThat(detail.path("realized_pnl").asLong()).isEqualTo(s.path("trade").path("realized_pnl").asLong());

        JsonNode list = body(call(HttpMethod.GET, "/api/v2/holdings", null, null));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).has("trades")).as("清單不含交易明細").isFalse();

        JsonNode sum = body(call(HttpMethod.GET, "/api/v2/summary", null, null));
        assertThat(sum.path("total_cost").asLong()).isEqualTo(1_241_766);
        assertThat(sum.path("realized_pnl").asLong()).isEqualTo(detail.path("realized_pnl").asLong());
        assertThat(sum.path("open_positions").asInt()).isEqualTo(1);
    }

    @Test
    void retriedRequestWithSameKeyIsRecordedOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String json = buy("2330", "2026-11-02", 1000, "1000");
        ResponseEntity<String> first = call(HttpMethod.POST, "/api/v2/trades", json, key);
        ResponseEntity<String> retry = call(HttpMethod.POST, "/api/v2/trades", json, key);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(retry.getStatusCode().value()).isEqualTo(201);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(body(retry).path("trade").path("id").asLong()).isEqualTo(body(first).path("trade").path("id").asLong());
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void retryWhileFirstStillRunningIsNotDoubleRecorded() throws Exception {
        FAKE.delayMs.set(1500); // 新代號要查官方，故意讓第一次很慢（約 3 秒）
        String key = UUID.randomUUID().toString();
        String json = buy("6488", "2026-11-02", 100, "600");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> first = pool.submit(() -> call(HttpMethod.POST, "/api/v2/trades", json, key));
        Thread.sleep(300); // App 沒等到回應就重送
        Future<ResponseEntity<String>> resend = pool.submit(() -> call(HttpMethod.POST, "/api/v2/trades", json, key));
        List<Integer> codes = List.of(first.get().getStatusCode().value(), resend.get().getStatusCode().value());
        pool.shutdown();
        assertThat(codes).containsExactlyInAnyOrder(201, 409);
        assertThat(tradeRepository.count()).isEqualTo(1);

        // 之後再重送：拿到第一次的結果
        ResponseEntity<String> later = call(HttpMethod.POST, "/api/v2/trades", json, key);
        assertThat(later.getStatusCode().value()).isEqualTo(201);
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void keyRequiredAndCannotBeReusedForDifferentRequest() throws Exception {
        ResponseEntity<String> noKey = call(HttpMethod.POST, "/api/v2/trades", buy("2330", "2026-11-02", 1, "1000"), null);
        assertThat(noKey.getStatusCode().value()).isEqualTo(400);
        assertThat(body(noKey).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");

        String key = UUID.randomUUID().toString();
        call(HttpMethod.POST, "/api/v2/trades", buy("2330", "2026-11-02", 1, "1000"), key);
        ResponseEntity<String> reused = call(HttpMethod.POST, "/api/v2/trades", buy("2330", "2026-11-02", 2, "1000"), key);
        assertThat(reused.getStatusCode().value()).isEqualTo(422);
        assertThat(body(reused).path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void errorResultIsAlsoReplayedButTransientErrorIsNot() throws Exception {
        String key = UUID.randomUUID().toString();
        String bad = sell("2330", "2026-11-02", 1, "1000");
        assertThat(call(HttpMethod.POST, "/api/v2/trades", bad, key).getStatusCode().value()).isEqualTo(409);
        assertThat(call(HttpMethod.POST, "/api/v2/trades", bad, key).getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        FAKE.twseDown.set(true);
        FAKE.tpexDown.set(true);
        String key2 = UUID.randomUUID().toString();
        String json = buy("2330", "2026-11-02", 1, "1000");
        assertThat(call(HttpMethod.POST, "/api/v2/trades", json, key2).getStatusCode().value()).isEqualTo(503);
        FAKE.twseDown.set(false);
        FAKE.tpexDown.set(false);
        ResponseEntity<String> retry = call(HttpMethod.POST, "/api/v2/trades", json, key2);
        assertThat(retry.getStatusCode().value()).as("503 不記住，重送會真的再做一次").isEqualTo(201);
    }

    @Test
    void oversellIs409AndNothingSaved() throws Exception {
        post(buy("2330", "2026-11-02", 1000, "1000"));
        ResponseEntity<String> r = post(sell("2330", "2026-11-03", 1500, "1000"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(body(r).path("error").path("code").asText()).isEqualTo("INSUFFICIENT_SHARES");
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void updateWithStaleVersionIsConflict() throws Exception {
        JsonNode t = body(post(buy("2330", "2026-11-02", 1000, "1000"))).path("trade");
        long id = t.path("id").asLong();
        long v = t.path("version").asLong();
        String upd = "{\"date\":\"2026-11-02\",\"shares\":1500,\"price\":990,\"version\":" + v + "}";
        ResponseEntity<String> ok = call(HttpMethod.PUT, "/api/v2/trades/" + id, upd, UUID.randomUUID().toString());
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(body(ok).path("trade").path("shares").asLong()).isEqualTo(1500);

        ResponseEntity<String> stale = call(HttpMethod.PUT, "/api/v2/trades/" + id,
                upd.replace("1500", "2000"), UUID.randomUUID().toString());
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(body(stale).path("error").path("code").asText()).isEqualTo("VERSION_CONFLICT");

        ResponseEntity<String> side = call(HttpMethod.PUT, "/api/v2/trades/" + id,
                "{\"side\":\"SELL\",\"date\":\"2026-11-02\",\"shares\":1,\"price\":1}", UUID.randomUUID().toString());
        assertThat(side.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void deleteTradeAndHolding() throws Exception {
        JsonNode r = body(post(buy("2330", "2026-11-02", 1000, "1000")));
        long tradeId = r.path("trade").path("id").asLong();
        long holdingId = r.path("holding").path("id").asLong();
        post(buy("2330", "2026-11-03", 1000, "1000"));

        assertThat(call(HttpMethod.DELETE, "/api/v2/trades/" + tradeId + "?version=99", null, UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(409);
        assertThat(call(HttpMethod.DELETE, "/api/v2/trades/" + tradeId, null, UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(204);
        assertThat(body(call(HttpMethod.GET, "/api/v2/holdings/" + holdingId, null, null)).path("shares").asLong()).isEqualTo(1000);

        assertThat(call(HttpMethod.DELETE, "/api/v2/holdings/" + holdingId, null, UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/v2/holdings/" + holdingId, null, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void closedPositionsHiddenUnlessAsked() throws Exception {
        post(buy("2330", "2026-11-02", 1000, "1000"));
        post(sell("2330", "2026-11-03", 1000, "1100"));
        assertThat(body(call(HttpMethod.GET, "/api/v2/holdings", null, null)).size()).isZero();
        JsonNode all = body(call(HttpMethod.GET, "/api/v2/holdings?include_closed=true", null, null));
        assertThat(all.size()).isEqualTo(1);
        assertThat(all.get(0).path("shares").asLong()).isZero();
        assertThat(all.get(0).path("avg_cost").isNull()).isTrue();
        assertThat(all.get(0).path("realized_pnl").asLong()).isEqualTo(1_100_000 - 1_567 - 3_300 - 1_001_425);
        // 1.0 App 看不到已賣光的部位
        assertThat(body(call(HttpMethod.GET, "/api/v1/holdings", null, null)).size()).isZero();
    }

    @Test
    void v1SeesOddLotsAsFloorOfLotsWithFullPnl() throws Exception {
        post(buy("2330", "2026-11-02", 1500, "1000"));
        JsonNode v1 = body(call(HttpMethod.GET, "/api/v1/holdings", null, null)).get(0);
        assertThat(v1.path("lots").isInt()).isTrue();
        assertThat(v1.path("lots").asInt()).isEqualTo(1);
        assertThat(v1.path("buy_price").decimalValue()).isEqualByComparingTo("1000");
        JsonNode v2 = body(call(HttpMethod.GET, "/api/v2/holdings", null, null)).get(0);
        assertThat(v1.path("pnl").asLong()).isEqualTo(v2.path("unrealized_pnl").asLong());
    }
}
