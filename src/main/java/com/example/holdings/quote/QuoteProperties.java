package com.example.holdings.quote;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 行情來源設定。主機部分可用環境變數 TWSE_BASE_URL、TPEX_BASE_URL 換掉（見 application.properties）。
 */
@ConfigurationProperties(prefix = "app.quote")
public record QuoteProperties(
        String twseBaseUrl,
        String tpexBaseUrl,
        /** 同一個官方主機兩次查詢之間至少隔多久（證交所會擋短時間大量查詢）。 */
        Duration minInterval,
        Duration connectTimeout,
        Duration requestTimeout,
        /** 新增持股時查代號的總時限（題目要求 10 秒內有結果，留餘裕）。 */
        Duration lookupDeadline,
        /** 背景更新多久跑一次。 */
        Duration refreshInterval,
        /** 某檔查過但還不是最新，最快多久再查一次。 */
        Duration recheckInterval,
        /** 某個官方主機出錯（連不上、被擋）後，背景更新暫停查它多久。 */
        Duration errorCooldown,
        /** 預期交易日當天幾點之後還查不到該日資料，就當作休市，不再重查到下一個交易日。 */
        String givenUpAfter,
        boolean refreshEnabled,
        /** false（預設）：證交所、櫃買中心共用同一個間隔，任兩次官方查詢都至少隔 minInterval；true：各主機分開算。 */
        boolean throttlePerHost) {

    public QuoteProperties {
        twseBaseUrl = stripSlash(twseBaseUrl == null ? "https://www.twse.com.tw" : twseBaseUrl);
        tpexBaseUrl = stripSlash(tpexBaseUrl == null ? "https://www.tpex.org.tw" : tpexBaseUrl);
        minInterval = minInterval == null ? Duration.ofSeconds(3) : minInterval;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(3) : requestTimeout;
        lookupDeadline = lookupDeadline == null ? Duration.ofSeconds(9) : lookupDeadline;
        refreshInterval = refreshInterval == null ? Duration.ofMinutes(10) : refreshInterval;
        recheckInterval = recheckInterval == null ? Duration.ofMinutes(10) : recheckInterval;
        errorCooldown = errorCooldown == null ? Duration.ofMinutes(5) : errorCooldown;
        givenUpAfter = givenUpAfter == null ? "18:00" : givenUpAfter;
    }

    private static String stripSlash(String url) {
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
