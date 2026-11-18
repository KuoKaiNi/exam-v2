package com.example.holdings.domain;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 被動式債券 ETF（代號 00 開頭、B 結尾）免徵證交稅的最後一天。
 * 依證券交易稅條例第 2 條之 1，目前的免徵期間到此日為止；2026-10-01 的修法草案要延長到 2036 年底，尚未三讀。
 * 日期未經完整確認，見 ISSUES.md，修法定案後改設定檔即可。
 */
@ConfigurationProperties(prefix = "tax")
public record TaxProperties(LocalDate bondEtfExemptUntil) {
}
