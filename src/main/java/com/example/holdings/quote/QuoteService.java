package com.example.holdings.quote;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.holdings.holding.HoldingException;

/**
 * 行情查詢與快取。
 * <ul>
 * <li>新增持股時：用代號依序問證交所、櫃買中心，判斷上市或上櫃，順便拿到名稱與最近收盤價。</li>
 * <li>清單頁：只讀 stock_quote 表，不查官方。</li>
 * <li>背景更新（{@link QuoteRefresher}）：收盤價不是「應該看得到的最近交易日」的才去查。</li>
 * </ul>
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final int PREVIOUS_MONTH_CHECK_UNTIL_DAY = 15;

    private final OfficialQuoteClient client;
    private final StockQuoteRepository repository;
    private final QuoteProperties props;
    private final Clock clock;
    private final LocalTime givenUpAfter;
    /** 背景更新用：某個市場出錯後，到這個時間之前先不查它，免得被證交所擋更久。 */
    private final Map<Market, Instant> cooldownUntil = new ConcurrentHashMap<>();

    public QuoteService(OfficialQuoteClient client, StockQuoteRepository repository, QuoteProperties props, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.props = props;
        this.clock = clock;
        this.givenUpAfter = LocalTime.parse(props.givenUpAfter());
    }

    public Optional<StockQuote> find(String code) {
        return repository.findById(code);
    }

    public List<StockQuote> findAll(Iterable<String> codes) {
        return repository.findAllById(codes);
    }

    /**
     * 新增持股前確認代號存在，並把行情存進快取。
     *
     * @throws HoldingException STOCK_NOT_FOUND：兩邊都確定查無此代號；QUOTE_UNAVAILABLE：官方網站有一邊連不上，無法判斷
     */
    public StockQuote lookupAndCache(String code) {
        Optional<StockQuote> cached = repository.findById(code);
        if (cached.isPresent()) {
            return cached.get(); // 以前查到過就確定存在；收盤價由背景更新補
        }
        long deadline = System.nanoTime() + props.lookupDeadline().toNanos();
        YearMonth thisMonth = YearMonth.now(clock);
        Instant now = clock.instant();
        String twseError = null;
        String tpexError = null;

        // 1. 證交所本月
        try {
            Optional<MonthlyQuote> m = client.twseMonth(code, thisMonth, deadline);
            if (m.isPresent() && m.get().latest().isPresent()) {
                return save(code, Market.TWSE, m.get(), now);
            }
        } catch (QuoteSourceException e) {
            twseError = e.getMessage();
        }

        // 2. 櫃買中心本月：代號存在時 name 一定有值（就算這個月還沒交易）
        try {
            MonthlyQuote m = client.tpexMonth(code, thisMonth, deadline);
            if (!m.name().isEmpty()) {
                if (m.latest().isEmpty()) {
                    m = fallbackTpexPreviousMonth(code, thisMonth, deadline, m);
                }
                return save(code, Market.TPEX, m, now);
            }
        } catch (QuoteSourceException e) {
            tpexError = e.getMessage();
        }

        // 3. 證交所上個月：月初本月還沒有（或很少）交易日時，上市股票要看上個月才知道存在。
        //    證交所對「代號不存在」和「本月還沒交易」回的訊息一樣，無法區分；過了 15 號本月一定有交易日，
        //    就不多查這一次，讓查無代號的情況也能在 10 秒內回覆。
        if (twseError == null && LocalDate.now(clock).getDayOfMonth() <= PREVIOUS_MONTH_CHECK_UNTIL_DAY) {
            try {
                Optional<MonthlyQuote> m = client.twseMonth(code, thisMonth.minusMonths(1), deadline);
                if (m.isPresent() && m.get().latest().isPresent()) {
                    return save(code, Market.TWSE, m.get(), now);
                }
            } catch (QuoteSourceException e) {
                twseError = e.getMessage();
            }
        }

        if (twseError != null || tpexError != null) {
            log.warn("查代號 {} 無法判斷：證交所={}，櫃買中心={}", code, twseError, tpexError);
            throw new HoldingException(HoldingException.Kind.QUOTE_UNAVAILABLE,
                    "暫時連不上" + (twseError != null && tpexError != null ? "證交所、櫃買中心" : twseError != null ? "證交所" : "櫃買中心")
                            + "，無法確認 " + code + " 是否存在，資料沒有儲存，請稍後再試");
        }
        throw new HoldingException(HoldingException.Kind.STOCK_NOT_FOUND, "查無股票代號 " + code + "（上市、上櫃都沒有）");
    }

    private MonthlyQuote fallbackTpexPreviousMonth(String code, YearMonth thisMonth, long deadline, MonthlyQuote current) {
        try {
            MonthlyQuote prev = client.tpexMonth(code, thisMonth.minusMonths(1), deadline);
            return new MonthlyQuote(current.name(), prev.days());
        } catch (QuoteSourceException e) {
            return current; // 名稱已確定，收盤價之後由背景更新補
        }
    }

    private StockQuote save(String code, Market market, MonthlyQuote m, Instant now) {
        StockQuote q = new StockQuote(code, market, m.name().isEmpty() ? code : m.name());
        q.recordSuccess(m.name(), m.latest().orElse(null), now);
        try {
            return repository.save(q);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 兩台裝置同時新增同一個新代號：另一個請求已經存好了，沿用那一筆
            return repository.findById(code).orElseThrow(() -> e);
        }
    }

    /** 測試用：清除出錯冷卻。 */
    void clearCooldowns() {
        cooldownUntil.clear();
    }

    /** 這檔現在需不需要向官方重查。 */
    public boolean needsRefresh(StockQuote q) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate expected = TradingCalendar.expectedLatestTradingDate(now);
        if (q.getPriceDate() != null && !q.getPriceDate().isBefore(expected)) {
            return false; // 已經是最新
        }
        Instant checked = q.getCheckedAt();
        if (checked == null) {
            return true;
        }
        // 預期交易日 18:00 之後查過還是沒有那天的資料：當作休市（國定假日、颱風假），等下一個交易日再查
        Instant givenUp = expected.atTime(givenUpAfter).atZone(now.getZone()).toInstant();
        if (!checked.isBefore(givenUp)) {
            return false;
        }
        return Duration.between(checked, now.toInstant()).compareTo(props.recheckInterval()) >= 0;
    }

    /** 背景更新一檔。出錯只記錄，不丟例外。 */
    public void refresh(StockQuote q) {
        Instant now = clock.instant();
        Instant until = cooldownUntil.get(q.getMarket());
        if (until != null && now.isBefore(until)) {
            return;
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        YearMonth thisMonth = YearMonth.now(clock);
        try {
            MonthlyQuote m = fetch(q.getCode(), q.getMarket(), thisMonth, deadline);
            if (m.latest().isEmpty()) {
                MonthlyQuote prev = fetch(q.getCode(), q.getMarket(), thisMonth.minusMonths(1), deadline);
                m = new MonthlyQuote(m.name().isEmpty() ? prev.name() : m.name(), prev.days());
            }
            q.recordSuccess(m.name(), m.latest().orElse(null), clock.instant());
            cooldownUntil.remove(q.getMarket());
        } catch (QuoteSourceException e) {
            log.warn("更新 {} 行情失敗：{}", q.getCode(), e.getMessage());
            q.recordError(e.getMessage(), clock.instant());
            cooldownUntil.put(q.getMarket(), clock.instant().plus(props.errorCooldown()));
        }
        repository.save(q);
    }

    private MonthlyQuote fetch(String code, Market market, YearMonth month, long deadline) throws QuoteSourceException {
        if (market == Market.TWSE) {
            return client.twseMonth(code, month, deadline).orElse(new MonthlyQuote("", List.of()));
        }
        return client.tpexMonth(code, month, deadline);
    }
}
