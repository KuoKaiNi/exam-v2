package com.example.holdings.holding;

/**
 * 業務錯誤。kind 對應 API 的錯誤代碼，message 直接給使用者看。
 */
public class HoldingException extends RuntimeException {

    public enum Kind {
        /** 400 */ VALIDATION_ERROR,
        /** 404 */ NOT_FOUND,
        /** 404 */ STOCK_NOT_FOUND,
        /** 409 */ DUPLICATE_CODE,
        /** 409：賣出超過持有股數 */ INSUFFICIENT_SHARES,
        /** 409：資料已被別的裝置改過（version 不符） */ VERSION_CONFLICT,
        /** 409：這檔在 2.0 有多筆交易，App 1.0 的「修改」無法表達 */ HAS_TRADE_HISTORY,
        /** 409：同一個 Idempotency-Key 的第一次請求還在處理 */ REQUEST_IN_PROGRESS,
        /** 422：同一個 Idempotency-Key 用在不同的請求內容 */ IDEMPOTENCY_KEY_REUSED,
        /** 503 */ QUOTE_UNAVAILABLE
    }

    private final Kind kind;

    public HoldingException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static HoldingException validation(String message) {
        return new HoldingException(Kind.VALIDATION_ERROR, message);
    }

    public static HoldingException notFound(long id) {
        return new HoldingException(Kind.NOT_FOUND, "找不到編號 " + id + " 的持股");
    }

    public static HoldingException tradeNotFound(long id) {
        return new HoldingException(Kind.NOT_FOUND, "找不到編號 " + id + " 的交易紀錄");
    }
}
