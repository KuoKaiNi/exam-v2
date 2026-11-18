package com.example.holdings.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** 輸入欄位規則。網頁與 API 共用，確保兩邊判斷一致。 */
public final class HoldingRules {

    private static final Pattern CODE = Pattern.compile("^\\d{4,6}[A-Z]?$");
    public static final int MIN_LOTS = 1;
    public static final int MAX_LOTS = 999;

    private HoldingRules() {
    }

    /** 代號去空白、轉大寫後檢查格式，回傳正規化後的代號。 */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("股票代號必填");
        }
        String code = raw.trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw invalid("股票代號格式不對，應為 4 到 6 碼數字（可加一個英文字母）");
        }
        return code;
    }

    public static void checkLots(Integer lots) {
        if (lots == null || lots < MIN_LOTS || lots > MAX_LOTS) {
            throw invalid("張數必須是 1 到 999 的整數");
        }
    }

    /** 股數：1 到 999,999 股，零股也可以。 */
    public static void checkShares(Integer shares) {
        if (shares == null || shares < 1 || shares > 999_999) {
            throw invalid("股數必須是 1 到 999,999 的整數");
        }
    }

    /** 交易日期：必填，且不能是未來日期。 */
    public static void checkTradeDate(LocalDate date, LocalDate today) {
        if (date == null) {
            throw invalid("交易日期必填");
        }
        if (date.isAfter(today)) {
            throw invalid("交易日期不能是未來日期");
        }
    }

    public static void checkBuyPrice(BigDecimal buyPrice) {
        if (buyPrice == null) {
            throw invalid("買進價必填");
        }
        if (buyPrice.signum() <= 0) {
            throw invalid("買進價必須大於 0");
        }
        if (buyPrice.stripTrailingZeros().scale() > 2) {
            throw invalid("買進價最多兩位小數");
        }
    }

    static HoldingException invalid(String message) {
        return new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
