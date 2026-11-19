package com.example.holdings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HomePageTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void homeShowsTitleAndTaipeiTime() {
        String html = rest.getForObject("/", String.class);
        assertThat(html).contains("持股記帳").contains("目前時間").contains("Asia/Taipei");
    }
}
