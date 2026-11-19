package com.example.holdings.holding;

/**
 * 業務錯誤。code 對應 API 規格的錯誤代碼（VALIDATION_ERROR、DUPLICATE_CODE…），message 直接給使用者看。
 */
public class HoldingException extends RuntimeException {

    public enum Kind {
        VALIDATION_ERROR, NOT_FOUND, STOCK_NOT_FOUND, DUPLICATE_CODE, QUOTE_UNAVAILABLE
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
}
