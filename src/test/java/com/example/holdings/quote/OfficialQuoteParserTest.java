package com.example.holdings.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 樣本取自 2026-11-19 實際查詢的回應（節錄）。 */
class OfficialQuoteParserTest {

    @Test
    void parsesTwseLatestCloseAndName() throws Exception {
        String body = """
                {"stat":"OK","date":"20261101","title":"115年11月 2330 台積電           各日成交資訊",
                 "fields":["日期","成交股數","成交金額","開盤價","最高價","最低價","收盤價","漲跌價差","成交筆數","註記"],
                 "data":[["115/11/18","1","1","1","1","1","2,175.00","+0","1",""],
                         ["115/11/19","56,582,314","120,506,549,868","2,165.00","2,190.00","2,085.00","2,100.00","-75.00","331,916",""]],
                 "notes":[],"total":2}
                """;
        MonthlyQuote m = OfficialQuoteParser.parseTwse("2330", body).orElseThrow();
        assertThat(m.name()).isEqualTo("台積電");
        assertThat(m.latest().orElseThrow().date()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(m.latest().orElseThrow().close()).isEqualByComparingTo("2100.00");
    }

    @Test
    void twseNoDataIsEmptyNotError() throws Exception {
        assertThat(OfficialQuoteParser.parseTwse("6488", "{\"stat\":\"很抱歉，沒有符合條件的資料!\",\"total\":0}")).isEmpty();
        assertThat(OfficialQuoteParser.parseTwse("2330", "{\"stat\":\"查詢日期大於今日，請重新查詢!\",\"total\":0}")).isEmpty();
    }

    @Test
    void skipsDaysWithoutTrades() throws Exception {
        String body = """
                {"stat":"OK","title":"115年11月 1234 某股 各日成交資訊",
                 "data":[["115/11/18","0","0","10.00","10.00","10.00","10.00","0","1",""],
                         ["115/11/19","0","0","--","--","--","--"," 0.00","0",""]]}
                """;
        DailyClose latest = OfficialQuoteParser.parseTwse("1234", body).orElseThrow().latest().orElseThrow();
        assertThat(latest.date()).isEqualTo(LocalDate.of(2026, 11, 18));
    }

    @Test
    void htmlResponseIsSourceError() {
        assertThatThrownBy(() -> OfficialQuoteParser.parseTwse("2330", "<html>請稍後再試</html>"))
                .isInstanceOf(QuoteSourceException.class);
        assertThatThrownBy(() -> OfficialQuoteParser.parseTpex(""))
                .isInstanceOf(QuoteSourceException.class);
    }

    @Test
    void parsesTpex() throws Exception {
        String body = """
                {"tables":[{"title":"個股日成交資訊","subtitle":"6488 環球晶 115年11月","date":"20261101",
                  "data":[["115/11/18","9,367","5,047,518","573.00","577.00","507.00","544.00","-2.00","15,718"],
                          ["115/11/19","9,738","4,906,510","551.00","551.00","490.00","490.00","-54.00","21,283"]],
                  "fields":[],"notes":[],"totalCount":2,"summary":[]}],
                 "date":"20261101","code":"6488","name":"環球晶","stat":"ok"}
                """;
        MonthlyQuote m = OfficialQuoteParser.parseTpex(body);
        assertThat(m.name()).isEqualTo("環球晶");
        assertThat(m.latest().orElseThrow().close()).isEqualByComparingTo("490");
    }

    @Test
    void tpexUnknownCodeHasEmptyName() throws Exception {
        String body = """
                {"tables":[{"title":"個股日成交資訊","subtitle":"9999  115年11月","date":"20261101","data":[],
                  "fields":[],"notes":[],"totalCount":0,"summary":[]}],
                 "date":"20261101","code":"9999","name":"","stat":"ok"}
                """;
        MonthlyQuote m = OfficialQuoteParser.parseTpex(body);
        assertThat(m.name()).isEmpty();
        assertThat(m.latest()).isEmpty();
    }

    @Test
    void rocDate() {
        assertThat(OfficialQuoteParser.parseRocDate("115/06/05")).contains(LocalDate.of(2026, 6, 5));
        assertThat(OfficialQuoteParser.parseRocDate("bad")).isEmpty();
    }
}
