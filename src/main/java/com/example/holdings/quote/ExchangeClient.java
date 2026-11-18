package com.example.holdings.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 證交所（上市）與櫃買中心（上櫃）的月資料 API。
 * 只負責連線與解析，快取與查詢流程在 QuoteService。
 */
@Component
public class ExchangeClient {

    static final String TWSE_NO_DATA = "很抱歉，沒有符合條件的資料";
    private static final Pattern TWSE_TITLE_NAME =
            Pattern.compile("\\d+年\\d+月\\s+\\S+\\s+(.+?)\\s+各日成交資訊");
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    static final int CLOSE_INDEX = 6;

    /** 某個月的查詢結果。found=false 表示這個市場查無此代號。 */
    public record StockMonth(boolean found, String name, List<DayClose> closes) {
    }

    /** 一個交易日的收盤價（只放有成交價的日子）。 */
    public record DayClose(LocalDate date, BigDecimal close) {
    }

    private final QuoteProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http;
    private final Map<Market, Instant> backoffUntil = new ConcurrentHashMap<>();
    private final Object throttleLock = new Object();
    private long lastRequestAtMillis;

    public ExchangeClient(QuoteProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public StockMonth fetch(Market market, String code, YearMonth month, Duration timeout) {
        Instant until = backoffUntil.get(market);
        if (until != null && Instant.now(clock).isBefore(until)) {
            throw new SourceUnavailableException(market.label() + "行情暫停查詢中");
        }
        throttle();
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(market, code, month))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            return market == Market.TWSE ? parseTwse(root) : parseTpex(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed(market, e);
        } catch (IOException | RuntimeException e) {
            throw failed(market, e);
        }
    }

    /** 同一個程序內連續對官方發出的請求至少間隔 min-request-interval，避免短時間大量查詢被擋。 */
    private void throttle() {
        synchronized (throttleLock) {
            long wait = lastRequestAtMillis + properties.minRequestInterval().toMillis() - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }

    private SourceUnavailableException failed(Market market, Exception cause) {
        backoffUntil.put(market, Instant.now(clock).plus(properties.failureBackoff()));
        return new SourceUnavailableException(market.label() + "行情連不上或格式不對", cause);
    }

    URI buildUri(Market market, String code, YearMonth month) {
        String base = market == Market.TWSE ? properties.twseBaseUrl() : properties.tpexBaseUrl();
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (market == Market.TWSE) {
            return URI.create(base + "/rwd/zh/afterTrading/STOCK_DAY?date="
                    + month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&stockNo=" + code + "&response=json");
        }
        return URI.create(base + "/www/zh-tw/afterTrading/tradingStock?code=" + code
                + "&date=" + month.atDay(1).format(ISO_DAY) + "&response=json");
    }

    /** 證交所：stat 為 OK 代表有資料；查無資料時 stat 是「很抱歉，沒有符合條件的資料!」。 */
    static StockMonth parseTwse(JsonNode root) throws IOException {
        String stat = text(root.get("stat"));
        if (stat == null) {
            throw new IOException("TWSE 回應沒有 stat");
        }
        if (stat.startsWith(TWSE_NO_DATA)) {
            return new StockMonth(false, null, List.of());
        }
        if (!"OK".equals(stat)) {
            throw new IOException("TWSE stat=" + stat);
        }
        String name = null;
        Matcher m = TWSE_TITLE_NAME.matcher(text(root.get("title")) == null ? "" : text(root.get("title")));
        if (m.find()) {
            name = m.group(1).trim();
        }
        return new StockMonth(true, name, closesFromRows(root.get("data")));
    }

    /** 櫃買中心：查無資料時 name 是空字串、data 是空陣列。 */
    static StockMonth parseTpex(JsonNode root) throws IOException {
        JsonNode tables = root.get("tables");
        if (tables == null || !tables.isArray() || tables.isEmpty()) {
            throw new IOException("TPEx 回應沒有 tables");
        }
        JsonNode data = tables.get(0).get("data");
        String name = text(root.get("name"));
        boolean hasName = name != null && !name.isBlank();
        boolean hasRows = data != null && data.isArray() && !data.isEmpty();
        if (!hasName && !hasRows) {
            return new StockMonth(false, null, List.of());
        }
        return new StockMonth(true, hasName ? name.trim() : null, closesFromRows(data));
    }

    private static List<DayClose> closesFromRows(JsonNode data) throws IOException {
        if (data == null || !data.isArray()) {
            throw new IOException("回應沒有 data 陣列");
        }
        List<DayClose> closes = new ArrayList<>();
        for (JsonNode row : data) {
            if (!row.isArray() || row.size() <= CLOSE_INDEX) {
                throw new IOException("資料列格式不對");
            }
            BigDecimal close = parseClose(row.get(CLOSE_INDEX).asText());
            if (close != null) {
                closes.add(new DayClose(parseRocDate(row.get(0).asText()), close));
            }
        }
        return closes;
    }

    /** 收盤價可能是 "2,330.00" 或 "--"（當天沒有成交）。 */
    static BigDecimal parseClose(String raw) {
        String text = raw == null ? "" : raw.replace(",", "").trim();
        if (text.isEmpty() || text.startsWith("-")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 民國年 "115/11/18" 轉西元 2026-11-18。 */
    static LocalDate parseRocDate(String raw) throws IOException {
        String[] parts = raw.trim().split("/");
        if (parts.length != 3) {
            throw new IOException("日期格式不對：" + raw);
        }
        try {
            return LocalDate.of(Integer.parseInt(parts[0]) + 1911, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            throw new IOException("日期格式不對：" + raw, e);
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
