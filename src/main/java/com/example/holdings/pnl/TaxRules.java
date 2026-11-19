package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 證交稅率（只有賣出收）。依代號與「賣出日期」決定：
 * <ul>
 * <li>一般股票 0.3%（證券交易稅條例第 2 條）</li>
 * <li>ETF（代號 00 開頭）0.1%</li>
 * <li>債券 ETF（代號 00 開頭、B 結尾，例如 00679B）：依證券交易稅條例第 2-1 條，
 *     2017-01-01 起至 {@code bondEtfExemptUntil}（現行條文 2026-12-31）停徵，稅率 0；期間外照 ETF 0.1%。
 *     槓桿型、反向型債券 ETF 不適用（它們的代號是 L、R 結尾，本來就不會被當成債券 ETF）。</li>
 * </ul>
 * 停徵延長的修正案還在立法院，三讀公布後只要改設定 {@code app.tax.bond-etf-exempt-until}，不用改程式。
 *
 * @param bondEtfExemptUntil 債券 ETF 停徵證交稅的最後一天（含）
 */
public record TaxRules(LocalDate bondEtfExemptUntil) {

    public static final LocalDate BOND_ETF_EXEMPT_FROM = LocalDate.of(2017, 1, 1);
    public static final TaxRules DEFAULT = new TaxRules(LocalDate.of(2026, 12, 31));

    static final BigDecimal STOCK_RATE = new BigDecimal("0.003");
    static final BigDecimal ETF_RATE = new BigDecimal("0.001");

    public BigDecimal rate(String code, LocalDate sellDate) {
        if (isBondEtf(code) && !sellDate.isBefore(BOND_ETF_EXEMPT_FROM) && !sellDate.isAfter(bondEtfExemptUntil)) {
            return BigDecimal.ZERO;
        }
        return isEtf(code) ? ETF_RATE : STOCK_RATE;
    }

    public static boolean isEtf(String code) {
        return code != null && code.startsWith("00");
    }

    /** 被動式債券 ETF：代號 00 開頭、B 結尾（例如 00679B 元大美債20年、00687B）。 */
    public static boolean isBondEtf(String code) {
        return isEtf(code) && code.endsWith("B");
    }
}
