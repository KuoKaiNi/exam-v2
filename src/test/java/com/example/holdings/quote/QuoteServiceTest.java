package com.example.holdings.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.holdings.holding.HoldingException;
import com.example.holdings.holding.HoldingService;
import com.example.holdings.support.IntegrationTestBase;

class QuoteServiceTest extends IntegrationTestBase {

    @Autowired
    QuoteService quoteService;
    @Autowired
    QuoteRefresher refresher;
    @Autowired
    HoldingService holdingService;

    @BeforeEach
    void clearCooldowns() {
        quoteService.clearCooldowns();
    }

    @Test
    void detectsListedStock() {
        StockQuote q = quoteService.lookupAndCache("2330");
        assertThat(q.getMarket()).isEqualTo(Market.TWSE);
        assertThat(q.getName()).isEqualTo("台積電");
        assertThat(q.getClosePrice()).isEqualByComparingTo("1100");
        assertThat(q.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(FAKE.requests()).hasSize(1);
    }

    @Test
    void detectsOtcStock() {
        StockQuote q = quoteService.lookupAndCache("6488");
        assertThat(q.getMarket()).isEqualTo(Market.TPEX);
        assertThat(q.getName()).isEqualTo("環球晶");
        assertThat(q.getClosePrice()).isEqualByComparingTo("630");
    }

    @Test
    void unknownCodeIsStockNotFound() {
        assertThatThrownBy(() -> quoteService.lookupAndCache("9999"))
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.STOCK_NOT_FOUND);
        assertThat(quoteRepository.findById("9999")).isEmpty();
    }

    @Test
    void unknownCodeAfterMidMonthNeedsOnlyTwoQueries() {
        CLOCK.set("2026-11-19T15:00");
        assertThatThrownBy(() -> quoteService.lookupAndCache("9999")).isInstanceOf(HoldingException.class);
        assertThat(FAKE.requests()).hasSize(2);
    }

    @Test
    void earlyInMonthListedStockFoundViaPreviousMonth() {
        CLOCK.set("2026-12-01T09:00"); // 12 月還沒有交易資料
        StockQuote q = quoteService.lookupAndCache("2330");
        assertThat(q.getMarket()).isEqualTo(Market.TWSE);
        assertThat(q.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void earlyInMonthOtcStockFoundViaPreviousMonth() {
        CLOCK.set("2026-12-01T09:00");
        StockQuote q = quoteService.lookupAndCache("6488");
        assertThat(q.getMarket()).isEqualTo(Market.TPEX);
        assertThat(q.getClosePrice()).isEqualByComparingTo("630");
    }

    @Test
    void sourceDownIsUnavailableNotNotFound() {
        FAKE.twseDown.set(true);
        assertThatThrownBy(() -> quoteService.lookupAndCache("9999"))
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.QUOTE_UNAVAILABLE);
    }

    @Test
    void otcStillFoundWhenTwseDown() {
        FAKE.twseDown.set(true);
        assertThat(quoteService.lookupAndCache("6488").getMarket()).isEqualTo(Market.TPEX);
    }

    @Test
    void refresherPicksUpNewCloseAfterPublishWithoutRestart() {
        holdingService.create("2330", "1", "1000");
        int calls = FAKE.requests().size();

        // 同一天再跑：已是最新，不查官方
        refresher.runOnce();
        assertThat(FAKE.requests()).hasSize(calls);

        // 隔天 13:30 收盤，官方還沒公布：會查，但查到的還是昨天
        CLOCK.set("2026-11-13T13:35");
        refresher.runOnce();
        assertThat(FAKE.requests()).hasSize(calls + 1);
        assertThat(quoteRepository.findById("2330").orElseThrow().getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 12));

        // 5 分鐘後：距上次查詢不到 10 分鐘，不查
        CLOCK.set("2026-11-13T13:40");
        refresher.runOnce();
        assertThat(FAKE.requests()).hasSize(calls + 1);

        // 官方公布了，10 分鐘後再查就拿到
        FAKE.addStock("2330", "台積電", true, "2026-11", new String[] {"115/11/12", "1,100.00"}, new String[] {"115/11/13", "1,120.00"});
        CLOCK.set("2026-11-13T13:46");
        refresher.runOnce();
        StockQuote q = quoteRepository.findById("2330").orElseThrow();
        assertThat(q.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(q.getClosePrice()).isEqualByComparingTo("1120");
    }

    @Test
    void holidayStopsRetryingAfterCutoff() {
        holdingService.create("2330", "1", "1000");
        CLOCK.set("2026-11-13T18:05"); // 假設 11/13 休市，官方沒有這天資料
        refresher.runOnce();
        int calls = FAKE.requests().size();
        CLOCK.set("2026-11-13T21:00");
        refresher.runOnce();
        assertThat(FAKE.requests()).as("18:00 後確認過沒有就不再重查").hasSize(calls);
    }

    @Test
    void refreshErrorKeepsOldPriceAndBacksOff() {
        holdingService.create("2330", "1", "1000");
        FAKE.twseDown.set(true);
        CLOCK.set("2026-11-13T14:00");
        refresher.runOnce();
        StockQuote q = quoteRepository.findById("2330").orElseThrow();
        assertThat(q.getClosePrice()).isEqualByComparingTo("1100");
        assertThat(q.getLastError()).isNotNull();

        int calls = FAKE.requests().size();
        CLOCK.set("2026-11-13T14:02"); // 冷卻 5 分鐘內不再查
        refresher.runOnce();
        assertThat(FAKE.requests()).hasSize(calls);
    }
}
