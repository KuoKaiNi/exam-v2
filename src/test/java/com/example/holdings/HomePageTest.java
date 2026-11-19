package com.example.holdings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import com.example.holdings.holding.HoldingService;
import com.example.holdings.support.IntegrationTestBase;

class HomePageTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    HoldingService holdingService;

    @Test
    void homeShowsTitleAndTaipeiTime() {
        String html = rest.getForObject("/", String.class);
        assertThat(html).contains("持股記帳").contains("目前時間").contains("2026-11-12 15:00:00").contains("Asia/Taipei");
    }

    @Test
    void listShowsNameAndCloseFromCache() {
        holdingService.create("2330", "1", "1000");
        holdingService.create("6488", "2", "600");
        int before = FAKE.requests().size();

        String html = rest.getForObject("/", String.class);

        assertThat(html).contains("台積電").contains("1,100.00").contains("上市")
                .contains("環球晶").contains("630.00").contains("上櫃");
        assertThat(FAKE.requests()).as("打開清單不查官方").hasSize(before);
    }

    @Test
    void listShowsPnlWithTaiwanColorsAndTotal() {
        holdingService.create("2330", "1", "1000");   // 收 1100 → +93,708
        holdingService.create("0050", "2", "150");    // 收 145.5 → -10,132

        String html = rest.getForObject("/", String.class);

        assertThat(html).contains("+93,708").contains("+9.36%").contains("-10,132").contains("-3.37%");
        assertThat(html).containsPattern("class=\"num pnl up\">\\+93,708");
        assertThat(html).containsPattern("class=\"num pnl down\">-10,132");
        assertThat(html).contains("id=\"total-pnl\" class=\"up\">+83,576 元");
        assertThat(html).contains("1,301,852").contains("1,391,000");
    }

    @Test
    void listStillRendersWhenOfficialSitesAreDown() {
        holdingService.create("2330", "1", "1000");
        quoteRepository.deleteAll(); // 模擬從沒查到過行情
        FAKE.twseDown.set(true);
        FAKE.tpexDown.set(true);

        var response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("2330").contains("未取得");
    }
}
