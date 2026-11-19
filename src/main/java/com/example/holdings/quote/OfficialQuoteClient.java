package com.example.holdings.quote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 只負責對官方網站送出查詢、拿回某個月的日成交資訊。路徑與參數固定照官方格式，主機可由設定替換。
 */
@Component
public class OfficialQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(OfficialQuoteClient.class);

    private final QuoteProperties props;
    private final HttpClient http;
    private final HostThrottle throttle;

    public OfficialQuoteClient(QuoteProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.throttle = new HostThrottle(props.minInterval());
    }

    /** 證交所某月資料；這個月查不到（代號不存在或該月還沒交易）回 empty。 */
    public Optional<MonthlyQuote> twseMonth(String code, YearMonth month, long deadlineNanos) throws QuoteSourceException {
        String url = props.twseBaseUrl() + "/rwd/zh/afterTrading/STOCK_DAY?date="
                + String.format("%04d%02d01", month.getYear(), month.getMonthValue())
                + "&stockNo=" + code + "&response=json";
        return OfficialQuoteParser.parseTwse(code, get(props.twseBaseUrl(), url, deadlineNanos));
    }

    /** 櫃買中心某月資料；代號不存在時 name 為空字串。 */
    public MonthlyQuote tpexMonth(String code, YearMonth month, long deadlineNanos) throws QuoteSourceException {
        String url = props.tpexBaseUrl() + "/www/zh-tw/afterTrading/tradingStock?code=" + code
                + "&date=" + String.format("%04d/%02d/01", month.getYear(), month.getMonthValue())
                + "&response=json";
        return OfficialQuoteParser.parseTpex(get(props.tpexBaseUrl(), url, deadlineNanos));
    }

    private String get(String host, String url, long deadlineNanos) throws QuoteSourceException {
        throttle.acquire(props.throttlePerHost() ? host : "official", deadlineNanos);
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            throw new QuoteSourceException("查詢逾時：" + url);
        }
        Duration timeout = Duration.ofNanos(Math.min(remaining, props.requestTimeout().toNanos()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (compatible; holdings-ledger)")
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("GET {} -> {} ({} ms)", url, response.statusCode(), (System.nanoTime() - start) / 1_000_000);
            if (response.statusCode() != 200) {
                throw new QuoteSourceException("HTTP " + response.statusCode() + "：" + url);
            }
            return response.body();
        } catch (IOException e) {
            log.warn("GET {} 失敗：{}", url, e.toString());
            throw new QuoteSourceException("連不上 " + host + "：" + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuoteSourceException("查詢被中斷", e);
        }
    }
}
