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
}
