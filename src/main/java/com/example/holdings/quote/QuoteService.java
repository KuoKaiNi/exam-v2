package com.example.holdings.quote;

import com.example.holdings.quote.ExchangeClient.DayClose;
import com.example.holdings.quote.ExchangeClient.StockMonth;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 最近一個交易日的收盤價與股票名稱。
 * 上市、上櫃都查：先查當月，沒有收盤價再查上個月（月初時當月可能還沒有資料）。
 * 收盤價有快取，同一檔在 cache-ttl 內不會重複向官方查。
 */
@Service
public class QuoteService {

    public record Quote(BigDecimal price, LocalDate date) {
    }

    /** 新增持股時查到的結果：名稱、市場、收盤價（查不到價格時為 null）。 */
    public record StockInfo(String code, String name, Market market, Quote quote) {
    }

    private record Probe(boolean found, String name, Quote quote) {
    }

    private record CacheEntry(Quote quote, Instant expiresAt) {
    }

    private final ExchangeClient client;
    private final QuoteProperties properties;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public QuoteService(ExchangeClient client, QuoteProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /** 持股清單用：取得收盤價，連不上或沒有價格時回傳空值（清單照樣顯示「未取得」）。 */
    public Optional<Quote> latestClose(String code, Market market) {
        String key = market + ":" + code;
        CacheEntry hit = cache.get(key);
        if (hit != null && Instant.now(clock).isBefore(hit.expiresAt())) {
            return Optional.ofNullable(hit.quote());
        }
        try {
            Probe probe = probe(market, code, null);
            if (probe.quote() != null) {
                cache.put(key, new CacheEntry(probe.quote(), Instant.now(clock).plus(properties.cacheTtl())));
            }
            return Optional.ofNullable(probe.quote());
        } catch (SourceUnavailableException e) {
            return Optional.empty();
        }
    }

    /**
     * 新增持股用：依序查上市、上櫃。
     * 回傳空值代表兩邊都確定查無此代號；兩邊有一邊連不上而另一邊沒找到，就丟出 SourceUnavailableException。
     */
    public Optional<StockInfo> lookup(String code) {
        Instant deadline = Instant.now(clock).plus(properties.lookupDeadline());
        boolean unavailable = false;
        for (Market market : Market.values()) {
            Probe probe;
            try {
                probe = probe(market, code, deadline);
            } catch (SourceUnavailableException e) {
                unavailable = true;
                continue;
            }
            if (probe.found()) {
                String name = probe.name() != null ? probe.name() : code;
                if (probe.quote() != null) {
                    cache.put(market + ":" + code,
                            new CacheEntry(probe.quote(), Instant.now(clock).plus(properties.cacheTtl())));
                }
                return Optional.of(new StockInfo(code, name, market, probe.quote()));
            }
        }
        if (unavailable) {
            throw new SourceUnavailableException("上市、上櫃至少有一邊連不上，無法確認代號");
        }
        return Optional.empty();
    }

    private Probe probe(Market market, String code, Instant deadline) {
        YearMonth thisMonth = YearMonth.now(clock);
        boolean found = false;
        String name = null;
        for (YearMonth month : List.of(thisMonth, thisMonth.minusMonths(1))) {
            StockMonth result = client.fetch(market, code, month, timeoutFor(deadline));
            if (result.found()) {
                found = true;
                if (name == null) {
                    name = result.name();
                }
                if (!result.closes().isEmpty()) {
                    DayClose last = result.closes().get(result.closes().size() - 1);
                    return new Probe(true, name, new Quote(last.close(), last.date()));
                }
            }
        }
        return new Probe(found, name, null);
    }

    private Duration timeoutFor(Instant deadline) {
        if (deadline == null) {
            return properties.readTimeout();
        }
        Duration remaining = Duration.between(Instant.now(clock), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new SourceUnavailableException("查詢逾時");
        }
        return remaining.compareTo(properties.readTimeout()) < 0 ? remaining : properties.readTimeout();
    }
}
