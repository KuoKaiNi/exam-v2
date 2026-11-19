package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.holdings.support.IntegrationTestBase;

class PortfolioServiceTest extends IntegrationTestBase {

    @Autowired
    PortfolioService service;
    @Autowired
    PortfolioQueryService query;

    private static HoldingException.Kind kind(Throwable e) {
        return ((HoldingException) e).getKind();
    }

    @Test
    void multipleBuysAndPartialSell() {
        service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        service.addTrade("2330", "BUY", "2026-11-05", "500", "1100");
        Trade sell = service.addTrade("2330", "SELL", "2026-11-10", "300", "1090");

        List<PositionView> list = query.positions(true);
        assertThat(list).hasSize(1);
        PositionView p = list.get(0);
        assertThat(p.shares()).isEqualTo(1200);
        assertThat(p.trades()).hasSize(3);
        assertThat(p.avgCost()).isEqualByComparingTo("1034.81"); // 1,552,208 ÷ 1,500
        assertThat(p.realizedPnl()).isNotZero();
        assertThat(p.unrealizedPnl()).isNotNull();
        assertThat(query.trade(sell.getId()).realizedPnl()).isEqualTo(p.realizedPnl());
    }

    @Test
    void oddLotsAreAllowed() {
        service.addTrade("2330", "BUY", "2026-11-02", "300", "1000");
        assertThat(query.positions(false).get(0).shares()).isEqualTo(300);
    }

    @Test
    void cannotSellMoreThanHeld() {
        service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        assertThatThrownBy(() -> service.addTrade("2330", "SELL", "2026-11-03", "1001", "1000"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.INSUFFICIENT_SHARES));
        assertThatThrownBy(() -> service.addTrade("2317", "SELL", "2026-11-03", "1", "100"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.INSUFFICIENT_SHARES));
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingABuyThatLaterSellsDependOnIsRejected() {
        Trade buy = service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        service.addTrade("2330", "SELL", "2026-11-03", "600", "1000");
        assertThatThrownBy(() -> service.deleteTrade(buy.getId(), null))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.INSUFFICIENT_SHARES));
        assertThatThrownBy(() -> service.updateTrade(buy.getId(), "2026-11-02", "500", "1000", null))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.INSUFFICIENT_SHARES));
        assertThat(tradeRepository.count()).isEqualTo(2);
    }

    @Test
    void staleVersionIsRejected() {
        Trade buy = service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        long v = buy.getVersion();
        service.updateTrade(buy.getId(), "2026-11-02", "2000", "1000", v);       // 手機改了
        assertThatThrownBy(() -> service.updateTrade(buy.getId(), "2026-11-02", "3000", "1000", v)) // 電腦拿舊畫面改
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.VERSION_CONFLICT));
        assertThat(query.positions(false).get(0).shares()).isEqualTo(2000);
    }

    @Test
    void deletingLastTradeRemovesPosition() {
        Trade buy = service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        service.deleteTrade(buy.getId(), null);
        assertThat(holdingRepository.count()).isZero();
    }

    @Test
    void futureDateAndBadInputAreValidationErrors() {
        assertThatThrownBy(() -> service.addTrade("2330", "BUY", "2026-11-13", "1000", "1000"))
                .hasMessageContaining("不能晚於今天");
        assertThatThrownBy(() -> service.addTrade("2330", "HOLD", "2026-11-02", "1000", "1000"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.addTrade("2330", "BUY", "2026/11/02", "0", "-1"))
                .hasMessageContaining("日期").hasMessageContaining("股數").hasMessageContaining("價格");
    }

    @Test
    void concurrentSellsFromTwoDevicesCannotOversell() throws Exception {
        service.addTrade("2330", "BUY", "2026-11-02", "1000", "1000");
        int n = 6;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Boolean> c = () -> {
                start.await();
                try {
                    service.addTrade("2330", "SELL", "2026-11-03", "400", "1000");
                    return true;
                } catch (HoldingException e) {
                    return false;
                }
            };
            results.add(pool.submit(c));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).as("1,000 股最多只能賣成功兩次 400 股").isEqualTo(2);
        assertThat(query.positions(true).get(0).shares()).isEqualTo(200);
    }

    @Test
    void concurrentFirstBuysOfSameNewCodeShareOnePosition() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                return service.addTrade("6488", "BUY", "2026-11-02", "100", "600");
            }));
        }
        start.countDown();
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        assertThat(holdingRepository.count()).isEqualTo(1);
        assertThat(query.positions(false).get(0).shares()).isEqualTo(400);
    }

    // ------------------------------------------------------------ App 1.0 相容

    @Test
    void v1CreateDuplicateAndReopenAfterSellingOut() {
        Holding h = service.v1Create("2330", "1", "1000");
        assertThatThrownBy(() -> service.v1Create("2330", "1", "900"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.DUPLICATE_CODE));
        service.addTrade("2330", "SELL", "2026-11-12", "1000", "1100");
        assertThatThrownBy(() -> service.v1Find(h.getId()))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.NOT_FOUND));
        Holding again = service.v1Create("2330", "2", "1050");
        assertThat(again.getId()).isEqualTo(h.getId());
        assertThat(query.position(h.getId()).shares()).isEqualTo(2000);
    }

    @Test
    void sellBeforeEstimatedV1BuyDateExplainsWhy() {
        service.v1Create("2330", "1", "1000"); // 日期記成今天 2026-11-12，標記為推估
        assertThatThrownBy(() -> service.addTrade("2330", "SELL", "2026-11-10", "500", "1100"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.INSUFFICIENT_SHARES))
                .hasMessageContaining("實際買進日");
    }

    @Test
    void v1UpdateOnlyWhenSingleBuy() {
        Holding h = service.v1Create("2330", "1", "1000");
        service.v1Update(h.getId(), "3", "990");
        assertThat(query.position(h.getId()).shares()).isEqualTo(3000);
        assertThat(query.position(h.getId()).avgPrice()).isEqualByComparingTo("990");

        service.addTrade("2330", "BUY", "2026-11-05", "500", "1100");
        assertThatThrownBy(() -> service.v1Update(h.getId(), "1", "1000"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.HAS_TRADE_HISTORY));
        assertThat(query.position(h.getId()).shares()).isEqualTo(3500);
    }

    @Test
    void unknownCodeIsRejectedAndNotSaved() {
        assertThatThrownBy(() -> service.v1Create("9999", "1", "10"))
                .satisfies(e -> assertThat(kind(e)).isEqualTo(HoldingException.Kind.STOCK_NOT_FOUND));
        assertThat(holdingRepository.count()).isZero();
    }
}
