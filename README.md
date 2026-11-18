# 持股記帳

輸入買進的股票，系統用官方收盤價算出「假設現在全部賣掉，淨賺或淨虧多少」。

## 需要先裝

- JDK 17 以上（開發時用 JDK 21 測過）
- 不需要另外裝資料庫或 Maven，`./mvnw` 會自己下載

## 啟動

```bash
./mvnw spring-boot:run
```

啟動後打開 <http://localhost:8083/>。

## 執行測試

```bash
./mvnw test
```
