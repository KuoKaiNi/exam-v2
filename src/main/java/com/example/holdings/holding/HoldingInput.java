package com.example.holdings.holding;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 把畫面表單或 API 送來的原始值（字串）檢查並轉成正確型別。
 * 網頁和 API 共用同一套規則。
 */
public final class HoldingInput {

    private static final Pattern CODE = Pattern.compile("^[0-9A-Z]{4,6}$");
    private static final Pattern INTEGER = Pattern.compile("^[0-9]+$");
    private static final Pattern PRICE = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private HoldingInput() {
    }

    public static String parseCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("股票代號必填");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw HoldingException.validation("股票代號格式不對：" + raw.trim() + "（應為 4～6 碼英數字，例如 2330、0050）");
        }
        return code;
    }

    /** 檢查張數與買進價，兩個都錯就一起回報。 */
    public static Values parseValues(String rawLots, String rawPrice) {
        List<String> errors = new ArrayList<>();
        Integer lots = null;
        BigDecimal price = null;
        try {
            lots = parseLots(rawLots);
        } catch (HoldingException e) {
            errors.add(e.getMessage());
        }
        try {
            price = parsePrice(rawPrice);
        } catch (HoldingException e) {
            errors.add(e.getMessage());
        }
        if (!errors.isEmpty()) {
            throw HoldingException.validation(String.join("；", errors));
        }
        return new Values(lots, price);
    }

    static int parseLots(String raw) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("張數必填");
        }
        String s = raw.trim();
        if (!INTEGER.matcher(s).matches() || s.length() > 4) {
            throw HoldingException.validation("張數要是 1～999 的整數，收到 " + s);
        }
        int lots = Integer.parseInt(s);
        if (lots < 1 || lots > 999) {
            throw HoldingException.validation("張數要是 1～999 的整數，收到 " + s);
        }
        return lots;
    }

    static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("買進價必填");
        }
        String s = raw.trim();
        if (!PRICE.matcher(s).matches()) {
            throw HoldingException.validation("買進價要是大於 0 的數字，收到 " + s);
        }
        BigDecimal price = new BigDecimal(s);
        if (price.signum() <= 0) {
            throw HoldingException.validation("買進價要大於 0");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw HoldingException.validation("買進價最多兩位小數，收到 " + s);
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw HoldingException.validation("買進價太大，收到 " + s);
        }
        return price.setScale(2);
    }

    public record Values(int lots, BigDecimal buyPrice) {
    }

    public static final long MAX_SHARES = 10_000_000L;
    private static final java.time.LocalDate EARLIEST = java.time.LocalDate.of(1962, 2, 9); // 證交所開業

    /** 2.0：股數（股），1～10,000,000 的整數，可以是零股。 */
    public static long parseShares(String raw) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("股數必填");
        }
        String s = raw.trim().replace(",", "");
        if (!INTEGER.matcher(s).matches() || s.length() > 9) {
            throw HoldingException.validation("股數要是 1～10,000,000 的整數，收到 " + raw.trim());
        }
        long shares = Long.parseLong(s);
        if (shares < 1 || shares > MAX_SHARES) {
            throw HoldingException.validation("股數要是 1～10,000,000 的整數，收到 " + raw.trim());
        }
        return shares;
    }

    /** 2.0：成交價，規則同 1.0 的買進價（大於 0、最多兩位小數）。 */
    public static BigDecimal parseTradePrice(String raw) {
        try {
            return parsePrice(raw);
        } catch (HoldingException e) {
            throw HoldingException.validation(e.getMessage().replace("買進價", "價格"));
        }
    }

    /** 2.0：交易日期 YYYY-MM-DD（西元），不能晚於今天（台北）。 */
    public static java.time.LocalDate parseDate(String raw, java.time.LocalDate today) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("日期必填（格式 YYYY-MM-DD）");
        }
        java.time.LocalDate d;
        try {
            d = java.time.LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw HoldingException.validation("日期格式要是 YYYY-MM-DD（西元年），收到 " + raw.trim());
        }
        if (d.isAfter(today)) {
            throw HoldingException.validation("日期不能晚於今天（" + today + "）");
        }
        if (d.isBefore(EARLIEST)) {
            throw HoldingException.validation("日期太早：" + d);
        }
        return d;
    }

    /** 2.0：買賣方向 BUY／SELL（也接受「買進」「賣出」）。 */
    public static com.example.holdings.pnl.PositionCalculator.Side parseSide(String raw) {
        if (raw == null || raw.isBlank()) {
            throw HoldingException.validation("買賣方向必填（BUY 或 SELL）");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "BUY", "買進", "買" -> com.example.holdings.pnl.PositionCalculator.Side.BUY;
            case "SELL", "賣出", "賣" -> com.example.holdings.pnl.PositionCalculator.Side.SELL;
            default -> throw HoldingException.validation("買賣方向要是 BUY 或 SELL，收到 " + raw.trim());
        };
    }

    /** version 欄位：可省略（null）；有給就要是非負整數。 */
    public static Long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (!INTEGER.matcher(s).matches() || s.length() > 18) {
            throw HoldingException.validation("version 要是整數，收到 " + s);
        }
        return Long.parseLong(s);
    }
}
