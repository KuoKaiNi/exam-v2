package com.example.holdings.support;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.holdings.holding.HoldingRepository;
import com.example.holdings.holding.TradeRepository;
import com.example.holdings.quote.StockQuoteRepository;

/**
 * 整合測試共用：記憶體資料庫、假官方伺服器、可調時鐘（預設 2026-11-12 週四 15:00 台北）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(IntegrationTestBase.ClockConfig.class)
public abstract class IntegrationTestBase {

    protected static final FakeOfficialServer FAKE = new FakeOfficialServer();
    protected static final MutableClock CLOCK = new MutableClock("2026-11-12T15:00");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.quote.twse-base-url", FAKE::baseUrl);
        r.add("app.quote.tpex-base-url", FAKE::baseUrl);
    }

    @Autowired
    protected HoldingRepository holdingRepository;
    @Autowired
    protected StockQuoteRepository quoteRepository;
    @Autowired
    protected TradeRepository tradeRepository;
    @Autowired
    protected com.example.holdings.idempotency.IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void resetState() {
        idempotencyRepository.deleteAllInBatch();
        tradeRepository.deleteAllInBatch();
        holdingRepository.deleteAllInBatch();
        quoteRepository.deleteAll();
        FAKE.reset();
        CLOCK.set("2026-11-12T15:00");
        // 預設幾檔股票（價格是測試用，不是真實股價）
        FAKE.addStock("2330", "台積電", true, "2026-11", new String[] {"115/11/11", "1,090.00"}, new String[] {"115/11/12", "1,100.00"});
        FAKE.addStock("0050", "元大台灣50", true, "2026-11", new String[] {"115/11/12", "145.50"});
        FAKE.addStock("2363", "矽統", true, "2026-11", new String[] {"115/11/12", "10.50"});
        FAKE.addStock("6488", "環球晶", false, "2026-11", new String[] {"115/11/12", "630.00"});
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }
}
