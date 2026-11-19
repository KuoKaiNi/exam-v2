package com.example.holdings.quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析證交所 STOCK_DAY 與櫃買中心 tradingStock 的 JSON。
 * 兩邊日期都是民國年（115/11/19），數字有千分位逗號；沒成交的日子收盤價是 "--"，跳過。
 */
public final class OfficialQuoteParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ROC_DATE = Pattern.compile("^\\s*(\\d{2,3})/(\\d{1,2})/(\\d{1,2})\\s*$");
    private static final int CLOSE_COLUMN = 6;

    private OfficialQuoteParser() {
    }

    /**
     * 證交所。stat 是 "OK" 才有資料；其他 stat（例如「很抱歉，沒有符合條件的資料!」）表示這個月查不到，回 empty。
     */
    public static Optional<MonthlyQuote> parseTwse(String code, String body) throws QuoteSourceException {
        JsonNode root = readJson(body);
        String stat = root.path("stat").asText("");
        if (!"OK".equalsIgnoreCase(stat.trim())) {
            if (stat.isEmpty()) {
                throw new QuoteSourceException("證交所回應沒有 stat 欄位");
            }
            return Optional.empty();
        }
        List<DailyClose> days = parseRows(root.path("data"));
        String name = twseName(code, root.path("title").asText(""));
        return Optional.of(new MonthlyQuote(name, days));
    }

    /** 櫃買中心。stat 一律是 "ok"；代號不存在時 name 是空字串。 */
    public static MonthlyQuote parseTpex(String body) throws QuoteSourceException {
        JsonNode root = readJson(body);
        String stat = root.path("stat").asText("");
        if (!"ok".equalsIgnoreCase(stat.trim())) {
            throw new QuoteSourceException("櫃買中心回應 stat=" + stat);
        }
        JsonNode tables = root.path("tables");
        JsonNode data = tables.isArray() && !tables.isEmpty() ? tables.get(0).path("data") : MAPPER.createArrayNode();
        return new MonthlyQuote(root.path("name").asText("").trim(), parseRows(data));
    }

    static String twseName(String code, String title) {
        // 例："115年11月 2330 台積電           各日成交資訊"
        String[] parts = title.trim().split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals(code)) {
                String next = parts[i + 1];
                return "各日成交資訊".equals(next) ? "" : next;
            }
        }
        return "";
    }

    private static List<DailyClose> parseRows(JsonNode data) {
        List<DailyClose> days = new ArrayList<>();
        if (!data.isArray()) {
            return days;
        }
        for (JsonNode row : data) {
            if (!row.isArray() || row.size() <= CLOSE_COLUMN) {
                continue;
            }
            Optional<LocalDate> date = parseRocDate(row.get(0).asText(""));
            Optional<BigDecimal> close = parseNumber(row.get(CLOSE_COLUMN).asText(""));
            if (date.isPresent() && close.isPresent() && close.get().signum() > 0) {
                days.add(new DailyClose(date.get(), close.get()));
            }
        }
        return days;
    }

    static Optional<LocalDate> parseRocDate(String s) {
        Matcher m = ROC_DATE.matcher(s);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(m.group(1)) + 1911,
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> parseNumber(String s) {
        String cleaned = s.replace(",", "").trim();
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static JsonNode readJson(String body) throws QuoteSourceException {
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node == null || !node.isObject()) {
                throw new QuoteSourceException("回應不是 JSON 物件");
            }
            return node;
        } catch (QuoteSourceException e) {
            throw e;
        } catch (Exception e) {
            // 證交所擋人時常回 HTML 頁面
            throw new QuoteSourceException("回應不是 JSON（可能被官方網站暫時擋下）", e);
        }
    }
}
