package com.example.holdings.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 測試用的假官方伺服器（在本機隨機 port），回應格式照證交所 STOCK_DAY、櫃買中心 tradingStock。
 * 測試絕不連到真的證交所或櫃買中心。
 */
public class FakeOfficialServer {

    public record Stock(String name, boolean twse, Map<String, List<String[]>> rowsByMonth) {
    }

    private final HttpServer server;
    private final Map<String, Stock> stocks = new ConcurrentHashMap<>();
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    public final AtomicBoolean twseDown = new AtomicBoolean();
    public final AtomicBoolean tpexDown = new AtomicBoolean();
    /** 每個回應前先等這麼多毫秒（模擬官方網站很慢）。 */
    public final java.util.concurrent.atomic.AtomicLong delayMs = new java.util.concurrent.atomic.AtomicLong();

    public FakeOfficialServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/rwd/zh/afterTrading/STOCK_DAY", this::twse);
        server.createContext("/www/zh-tw/afterTrading/tradingStock", this::tpex);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    public void reset() {
        stocks.clear();
        requests.clear();
        twseDown.set(false);
        tpexDown.set(false);
        delayMs.set(0);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    /** 新增一檔股票。month 格式 "2026-11"；rows 每列 {民國日期, 收盤價}，例如 {"115/11/12", "1,100.00"}。 */
    public void addStock(String code, String name, boolean twse, String month, String[]... rows) {
        Stock s = stocks.computeIfAbsent(code, c -> new Stock(name, twse, new ConcurrentHashMap<>()));
        s.rowsByMonth().put(month, List.of(rows));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> q = new HashMap<>();
        if (uri.getRawQuery() == null) {
            return q;
        }
        for (String part : uri.getRawQuery().split("&")) {
            String[] kv = part.split("=", 2);
            q.put(kv[0], kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return q;
    }

    private void twse(HttpExchange ex) throws IOException {
        requests.add("TWSE " + ex.getRequestURI());
        if (twseDown.get()) {
            send(ex, 503, "<html>blocked</html>");
            return;
        }
        Map<String, String> q = query(ex.getRequestURI());
        String code = q.get("stockNo");
        String date = q.get("date"); // 20261101
        String month = date.substring(0, 4) + "-" + date.substring(4, 6);
        Stock s = stocks.get(code);
        List<String[]> rows = s == null || !s.twse() ? null : s.rowsByMonth().get(month);
        if (rows == null || rows.isEmpty()) {
            send(ex, 200, "{\"stat\":\"很抱歉，沒有符合條件的資料!\",\"total\":0}");
            return;
        }
        int roc = Integer.parseInt(date.substring(0, 4)) - 1911;
        String title = roc + "年" + date.substring(4, 6) + "月 " + code + " " + s.name() + "           各日成交資訊";
        StringBuilder data = new StringBuilder();
        for (String[] r : rows) {
            if (data.length() > 0) {
                data.append(',');
            }
            data.append("[\"").append(r[0]).append("\",\"1,000\",\"1,000,000\",\"1.00\",\"1.00\",\"1.00\",\"")
                    .append(r[1]).append("\",\"+0.00\",\"10\",\"\"]");
        }
        send(ex, 200, "{\"stat\":\"OK\",\"date\":\"" + date + "\",\"title\":\"" + title
                + "\",\"fields\":[\"日期\",\"成交股數\",\"成交金額\",\"開盤價\",\"最高價\",\"最低價\",\"收盤價\",\"漲跌價差\",\"成交筆數\",\"註記\"],"
                + "\"data\":[" + data + "],\"notes\":[],\"total\":" + rows.size() + "}");
    }

    private void tpex(HttpExchange ex) throws IOException {
        requests.add("TPEX " + ex.getRequestURI());
        if (tpexDown.get()) {
            send(ex, 500, "error");
            return;
        }
        Map<String, String> q = query(ex.getRequestURI());
        String code = q.get("code");
        String date = q.get("date"); // 2026/11/01
        String month = date.substring(0, 4) + "-" + date.substring(5, 7);
        Stock s = stocks.get(code);
        boolean exists = s != null && !s.twse();
        List<String[]> rows = exists ? s.rowsByMonth().getOrDefault(month, List.of()) : List.of();
        StringBuilder data = new StringBuilder();
        for (String[] r : rows) {
            if (data.length() > 0) {
                data.append(',');
            }
            data.append("[\"").append(r[0]).append("\",\"1,000\",\"1,000\",\"1.00\",\"1.00\",\"1.00\",\"")
                    .append(r[1]).append("\",\"0.00\",\"10\"]");
        }
        String name = exists ? s.name() : "";
        send(ex, 200, "{\"tables\":[{\"title\":\"個股日成交資訊\",\"subtitle\":\"" + code + " " + name
                + "\",\"date\":\"" + date.replace("/", "") + "\",\"data\":[" + data
                + "],\"fields\":[\"日 期\",\"成交張數\",\"成交仟元\",\"開盤\",\"最高\",\"最低\",\"收盤\",\"漲跌\",\"筆數\"],\"notes\":[],\"totalCount\":"
                + rows.size() + ",\"summary\":[]}],\"date\":\"" + date.replace("/", "") + "\",\"code\":\"" + code
                + "\",\"name\":\"" + name + "\",\"stat\":\"ok\"}");
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        long d = delayMs.get();
        if (d > 0) {
            try {
                Thread.sleep(d);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
