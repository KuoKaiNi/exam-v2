package com.example.holdings.quote;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.holdings.holding.HoldingRepository;

import jakarta.annotation.PreDestroy;

/**
 * 背景更新收盤價。每 refreshInterval（預設 1 分鐘）檢查一次「哪些持股的收盤價不是最新」，
 * 只對那幾檔、而且距離上次查詢超過 recheckInterval（預設 10 分鐘）的才查官方。
 * 所以官方公布當天收盤價後，最慢約 11 分鐘清單上就看得到，不用重開伺服器。
 */
@Component
public class QuoteRefresher {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefresher.class);

    private final HoldingRepository holdings;
    private final QuoteService quotes;
    private final QuoteProperties props;
    private ScheduledExecutorService executor;

    public QuoteRefresher(HoldingRepository holdings, QuoteService quotes, QuoteProperties props) {
        this.holdings = holdings;
        this.quotes = quotes;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.refreshEnabled()) {
            log.info("背景更新收盤價已關閉（app.quote.refresh-enabled=false）");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "quote-refresher");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::runOnce, 5, props.refreshInterval().toSeconds(), TimeUnit.SECONDS);
    }

    /** 跑一輪。public 方便測試直接呼叫。 */
    public void runOnce() {
        try {
            List<String> codes = holdings.findAll().stream().map(h -> h.getCode()).distinct().toList();
            for (StockQuote q : quotes.findAll(codes)) {
                if (quotes.needsRefresh(q)) {
                    quotes.refresh(q);
                }
            }
        } catch (RuntimeException e) {
            log.error("背景更新收盤價失敗", e); // 不讓例外終止排程
        }
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
