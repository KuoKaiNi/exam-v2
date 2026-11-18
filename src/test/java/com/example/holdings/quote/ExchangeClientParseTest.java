package com.example.holdings.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 官方回應的解析，使用從證交所、櫃買中心實際回應擷取的格式（不連網路）。 */
class ExchangeClientParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 證交所有資料_取出名稱與有收盤價的日子() throws IOException {
        JsonNode root = mapper.readTree("""
                {"stat":"OK","date":"20261101","title":"115年11月 2330 台積電           各日成交資訊",
                 "fields":["日期","成交股數","成交金額","開盤價","最高價","最低價","收盤價","漲跌價差","成交筆數","註記"],
                 "data":[["115/11/17","61,610,981","135,465,361,000","2,200.00","2,210.00","2,150.00","2,175.00","+5.00","21,152",""],
                         ["115/11/18","0","0","--","--","--","--","0.00","0",""]]}
                """);

        ExchangeClient.StockMonth m = ExchangeClient.parseTwse(root);

        assertTrue(m.found());
        assertEquals("台積電", m.name());
        assertEquals(1, m.closes().size());
        assertEquals(LocalDate.of(2026, 11, 17), m.closes().get(0).date());
        assertEquals(new BigDecimal("2175.00"), m.closes().get(0).close());
    }

    @Test
    void 證交所查無資料_stat是很抱歉() throws IOException {
        JsonNode root = mapper.readTree("{\"stat\":\"很抱歉，沒有符合條件的資料!\",\"total\":0}");

        ExchangeClient.StockMonth m = ExchangeClient.parseTwse(root);

        assertFalse(m.found());
    }

    @Test
    void 證交所回應異常_丟出例外不當成查無資料() throws IOException {
        JsonNode root = mapper.readTree("{\"stat\":\"系統忙碌，請稍後再試\"}");

        assertThrows(IOException.class, () -> ExchangeClient.parseTwse(root));
    }

    @Test
    void 櫃買有資料_名稱在頂層() throws IOException {
        JsonNode root = mapper.readTree("""
                {"tables":[{"title":"個股日成交資訊","subtitle":"6488 環球晶 115年11月","date":"20261101",
                  "data":[["115/11/16","7,729","4,607,056","625.00","638.00","575.00","577.00","-53.00","19,399"],
                          ["115/11/17","10,450","5,735,050","544.00","575.00","535.00","546.00","-31.00","18,811"]]}],
                 "date":"20261101","code":"6488","name":"環球晶","stat":"ok"}
                """);

        ExchangeClient.StockMonth m = ExchangeClient.parseTpex(root);

        assertTrue(m.found());
        assertEquals("環球晶", m.name());
        assertEquals(new BigDecimal("546.00"), m.closes().get(1).close());
        assertEquals(LocalDate.of(2026, 11, 17), m.closes().get(1).date());
    }

    @Test
    void 櫃買查無資料_名稱空白且沒有列() throws IOException {
        JsonNode root = mapper.readTree("""
                {"tables":[{"title":"個股日成交資訊","data":[],"fields":[],"notes":[],"totalCount":0}],
                 "code":"9999","name":"","stat":"ok"}
                """);

        assertFalse(ExchangeClient.parseTpex(root).found());
    }

    @Test
    void 收盤價格式() {
        assertEquals(new BigDecimal("2330.00"), ExchangeClient.parseClose("2,330.00"));
        assertNull(ExchangeClient.parseClose("--"));
        assertNull(ExchangeClient.parseClose(""));
    }

    @Test
    void 民國年轉西元() throws IOException {
        assertEquals(LocalDate.of(2026, 11, 18), ExchangeClient.parseRocDate("115/11/18"));
    }
}
