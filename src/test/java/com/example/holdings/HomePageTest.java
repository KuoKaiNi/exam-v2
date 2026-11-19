package com.example.holdings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import com.example.holdings.holding.PortfolioService;
import com.example.holdings.support.IntegrationTestBase;

class HomePageTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    PortfolioService holdingService;

    @Test
    void homeShowsTitleAndTaipeiTime() {
        String html = rest.getForObject("/", String.class);
        assertThat(html).contains("持股記帳").contains("目前時間").contains("2026-11-12 15:00:00").contains("Asia/Taipei");
    }

    @Test
    void listShowsNameAndCloseFromCache() {
        holdingService.v1Create("2330", "1", "1000");
        holdingService.v1Create("6488", "2", "600");
        int before = FAKE.requests().size();

        String html = rest.getForObject("/", String.class);

        assertThat(html).contains("台積電").contains("1,100.00").contains("上市")
                .contains("環球晶").contains("630.00").contains("上櫃");
        assertThat(FAKE.requests()).as("打開清單不查官方").hasSize(before);
    }

    @Test
    void listShowsPnlWithTaiwanColorsAndTotal() {
        holdingService.v1Create("2330", "1", "1000");   // 收 1100 → +93,708
        holdingService.v1Create("0050", "2", "150");    // 收 145.5 → -10,132

        String html = rest.getForObject("/", String.class);

        assertThat(html).contains("+93,708").contains("+9.36%").contains("-10,132").contains("-3.37%");
        assertThat(html).containsPattern("class=\"num pnl up\">\\+93,708");
        assertThat(html).containsPattern("class=\"num pnl down\">-10,132");
        assertThat(html).contains("id=\"total-pnl\" class=\"up\">+83,576 元");
        assertThat(html).contains("1,301,852").contains("1,391,000");
        assertThat(html).contains("1,001.43"); // 2330 平均成本含手續費 1,001,425 ÷ 1,000
    }

    @Test
    void webTradeFlowWithDoubleSubmitAndDetailPage() {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("code", "2330"); form.add("side", "BUY"); form.add("date", "2026-11-02");
        form.add("shares", "1500"); form.add("price", "1000"); form.add("token", "tok-12345678");
        rest.postForEntity("/trades", form, String.class);
        rest.postForEntity("/trades", form, String.class); // 連按兩次
        assertThat(tradeRepository.count()).as("同一個表單送兩次只記一筆").isEqualTo(1);

        var sell = new org.springframework.util.LinkedMultiValueMap<String, String>();
        sell.add("code", "2330"); sell.add("side", "SELL"); sell.add("date", "2026-11-10");
        sell.add("shares", "500"); sell.add("price", "1100"); sell.add("token", "tok-87654321");
        rest.postForEntity("/trades", sell, String.class);

        long id = holdingRepository.findByCode("2330").orElseThrow().getId();
        String detail = rest.getForObject("/holdings/" + id, String.class);
        assertThat(detail).contains("台積電").contains("id=\"shares\">1,000<").contains("賣出").contains("已實現損益");
        String home = rest.getForObject("/", String.class);
        assertThat(home).contains("id=\"realized\"");
    }

    @Test
    void listStillRendersWhenOfficialSitesAreDown() {
        holdingService.v1Create("2330", "1", "1000");
        quoteRepository.deleteAll(); // 模擬從沒查到過行情
        FAKE.twseDown.set(true);
        FAKE.tpexDown.set(true);

        var response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("2330").contains("未取得");
    }
}
