package com.example.holdings.quote;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 對同一個官方主機，兩次查詢之間至少隔 minInterval。每個呼叫者先「預約」下一個空檔再等待，
 * 所以背景更新一次只佔一個空檔，使用者按新增時最多只排在一個請求後面。
 */
class HostThrottle {

    private final long intervalNanos;
    private final Map<String, Long> nextSlot = new HashMap<>();

    HostThrottle(Duration minInterval) {
        this.intervalNanos = minInterval.toNanos();
    }

    /**
     * 等到可以查詢為止。若要等的時間會超過 deadlineNanos（System.nanoTime 基準），直接丟出例外。
     */
    void acquire(String host, long deadlineNanos) throws QuoteSourceException {
        long slot;
        synchronized (this) {
            long now = System.nanoTime();
            slot = Math.max(now, nextSlot.getOrDefault(host, now));
            if (slot > deadlineNanos) {
                throw new QuoteSourceException("查詢排隊超過時限：" + host);
            }
            nextSlot.put(host, slot + intervalNanos);
        }
        long wait = slot - System.nanoTime();
        if (wait > 0) {
            try {
                Thread.sleep(Duration.ofNanos(wait).toMillis(), (int) (Duration.ofNanos(wait).toNanosPart() % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new QuoteSourceException("查詢被中斷", e);
            }
        }
    }
}
