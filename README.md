# 持股記帳

記下自己買了哪些台股，用證交所／櫃買中心的官方收盤價，算出「如果現在全部賣掉，淨賺或淨虧多少」。

**2.0**：股數用「股」記（可記零股）、同一檔可以分多次買進、可以賣出一部分（算已實現損益）。

| 文件 | 內容 |
|---|---|
| [API.md](API.md) | App 2.0 用的 `/api/v2`，以及 App 1.0 的 `/api/v1` 在 2.0 的行為 |
| [UPGRADE.md](UPGRADE.md) | 從 1.0 升級到 2.0 的步驟、資料庫變動、上線後注意事項 |
| [ISSUES.md](ISSUES.md) | 客服轉來三則回報的查證與處理 |
| [REVIEW.md](REVIEW.md) | 回顧 |

## 需要先裝什麼

- **Java 17 以上**（JDK，不是只有 JRE）。用 `java -version` 確認。
- 其他都不用裝：Maven 由專案內附的 Maven Wrapper（`mvnw`）自動下載；資料庫是 H2 檔案模式，會自動建在專案下的 `data/` 資料夾。
- 第一次啟動要能連網（下載 Maven 與套件）。

## 怎麼啟動

macOS／Linux：

```bash
./mvnw spring-boot:run
```

Windows：

```bat
mvnw.cmd spring-boot:run
```

看到 `Started HoldingsApplication` 就是啟動好了。

## 啟動後打開哪個網址

http://localhost:8085/

預設埠號是 8085。要換埠號，啟動前設環境變數 `PORT`，例如 `PORT=8080 ./mvnw spring-boot:run`。

## 時區

主機在海外、時區不是台北也沒關係：程式裡所有「現在幾點、今天是不是交易日」都固定用 `Asia/Taipei` 計算，不看主機時區。

## 資料庫

H2 檔案模式，資料存在 `data/holdings.mv.db`（第一次啟動自動建立）。資料表由 Flyway 建立，migration 檔在 `src/main/resources/db/migration/`。
要清空資料：停掉伺服器後刪除 `data/` 資料夾。要換位置：環境變數 `DB_URL`，例如 `DB_URL="jdbc:h2:file:/var/lib/holdings/holdings;LOCK_TIMEOUT=10000"`（請保留 `LOCK_TIMEOUT`，見 UPGRADE.md）。

從 1.0 升級：見 [UPGRADE.md](UPGRADE.md)。2.0 第一次啟動會自動轉換 1.0 的資料。

## 畫面

- 首頁 http://localhost:8085/ ：總計（未實現、已實現損益）、「記一筆交易」（買進或賣出、日期、股數、價格）、持股清單（股數、平均成本、收盤價、未實現與已實現損益）、已全部賣出的股票。
- 點代號進到這檔的明細頁：每一筆買賣的成交金額、手續費、證交稅、已實現損益；可以直接改日期／股數／價格或刪除。
- 賣出不能超過當時手上的股數；新代號第一次買進會向官方確認代號存在。

## 跑測試

```bash
./mvnw test
```

## 行情來源（證交所／櫃買中心）

收盤價一律取自官方：上市查臺灣證券交易所 `STOCK_DAY`，上櫃查證券櫃檯買賣中心 `tradingStock`。

### 換掉官方網站的主機（例如換成測試伺服器）

只換主機部分，路徑和參數不變。啟動前設環境變數：

```bash
TWSE_BASE_URL=http://localhost:9000 TPEX_BASE_URL=http://localhost:9000 ./mvnw spring-boot:run
```

也可以改 `src/main/resources/application.properties` 的 `app.quote.twse-base-url`、`app.quote.tpex-base-url`，
或在啟動指令後加參數：`./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.quote.twse-base-url=http://localhost:9000 --app.quote.tpex-base-url=http://localhost:9000"`。
網址結尾有沒有 `/` 都可以。

### 怎麼判斷上市還是上櫃

新增持股時只輸入代號，伺服器依序查：

1. 證交所本月資料，有成交資料 → 上市。
2. 櫃買中心本月資料，回應裡 `name` 有值 → 上櫃（櫃買中心對不存在的代號回空的 `name`）。
3. 每月 1～15 號再查證交所上個月（月初本月可能還沒有交易日，證交所對「代號不存在」和「本月還沒交易」回的訊息一樣）。

都沒有 → 顯示「查無股票代號」，不儲存。官方網站連不上、無法判斷時 → 顯示「暫時連不上…請稍後再試」，也不儲存。
整個查詢有 9 秒時限，超過就回錯誤，所以按下新增到看到結果不會超過 10 秒。

### 什麼時候查官方、什麼時候用快取

- 查到的名稱、市場、最近收盤價與日期存在資料表 `stock_quote`。**打開清單頁只讀資料庫，不查官方。**
- 背景每 1 分鐘檢查一次：用台北時間推算「現在應該看得到哪一天的收盤價」（平日 13:30 後是今天，之前是前一個平日；週末算週五）。
  資料庫裡的收盤價比這天舊、而且距離上次查詢超過 10 分鐘，才去查官方。所以官方公布當天收盤價後，最慢約 11 分鐘清單就更新，不用重開伺服器。
- 國定假日：官方沒有那天的資料，查到當天 18:00（台北）以後還是沒有，就當作休市，不再重查到下一個交易日。
- 任兩次官方查詢至少隔 3 秒（證交所會擋短時間大量查詢）。某一邊出錯（連不上、回的不是 JSON）後，背景更新暫停查那一邊 5 分鐘。
- 官方網站連不上時，清單頁照樣顯示，用資料庫裡上次查到的收盤價；從來沒查到過的那格顯示「未取得」。

相關設定都在 `application.properties` 的 `app.quote.*`。

## 盈虧怎麼算

程式在 `src/main/java/com/example/holdings/pnl/`（`PnlCalculator`、`PositionCalculator`、`TaxRules`）：

| 項目 | 規則 |
|---|---|
| 手續費 | 每筆交易：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元 |
| 證交稅 | 只有賣出收，無條件捨去到元。一般股票 0.3%，ETF（代號 00 開頭）0.1%，債券 ETF（00 開頭、B 結尾）2026-12-31 以前賣出免徵（ISSUES.md 回報二） |
| 平均成本 | 移動平均法，含買進手續費 |
| 已實現損益 | 每筆賣出：成交金額 − 手續費 − 證交稅 − 賣出部分的成本 |
| 未實現損益 | 假設現在用收盤價賣出剩下的股數：賣出金額 − 賣出手續費 − 證交稅 − 剩餘成本 |
| 收益率 | 未實現損益 ÷ 剩餘成本 × 100，四捨五入到小數第二位 |

只有一筆買進時，跟 1.0 的公式完全一樣。詳細例子見 [API.md](API.md) 第 5 節。紅色是賺、綠色是虧。
未取得收盤價的持股不算進市值和未實現損益，但算進總成本，總計下方會註明筆數。

單元測試：`PnlCalculatorTest`（1.0 題目的三個例子）、`PositionCalculatorTest`（分次買進、部分賣出、零股、超賣）、`TaxRulesTest`（債券 ETF 停徵）。
所有測試都不連證交所或櫃買中心：需要行情的整合測試用測試程式自己啟動的本機假伺服器。

## 給手機 App 用的 API

- **App 2.0**：`/api/v2`，規格見 [API.md](API.md)。寫入要帶 `Idempotency-Key`。
- **App 1.0**：`/api/v1`，下面這節；2.0 之後欄位與狀態碼不變，對照方式見 API.md 第 7 節。

### App 1.0（`/api/v1`）

完全照《App 1.0 API 規格》實作，路徑開頭 `/api/v1`，回應 JSON（UTF-8），欄位小寫加底線。

| 方法 | 路徑 | 成功 | 失敗 |
|---|---|---|---|
| GET | `/api/v1/holdings` | 200 持股陣列（沒有回 `[]`） | |
| GET | `/api/v1/holdings/{id}` | 200 | 404 `NOT_FOUND` |
| POST | `/api/v1/holdings` | 201 | 400 `VALIDATION_ERROR`、404 `STOCK_NOT_FOUND`、409 `DUPLICATE_CODE` |
| PUT | `/api/v1/holdings/{id}` | 200 | 400 `VALIDATION_ERROR`、404 `NOT_FOUND` |
| DELETE | `/api/v1/holdings/{id}` | 204 | 404 `NOT_FOUND` |
| GET | `/api/v1/summary` | 200 總計 | |

錯誤一律是 `{"error":{"code":"...","message":"..."}}`。規格以外的情況：
- 新增時官方網站連不上、無法確認代號是否存在：503 `QUOTE_UNAVAILABLE`（不儲存）。
- PUT 帶了不同的 `code`：400 `VALIDATION_ERROR`（代號不能改）。
- 2.0 之後：PUT 一檔有多筆買賣紀錄的股票：409 `HAS_TRADE_HISTORY`（資料不動）。
- `/api/` 底下不存在的路徑：404 `NOT_FOUND`；未預期的錯誤：500 `INTERNAL_ERROR`。

範例：

```bash
curl -X POST localhost:8085/api/v1/holdings -H 'Content-Type: application/json' -d '{"code":"2330","lots":1,"buy_price":1000}'
curl localhost:8085/api/v1/holdings
curl localhost:8085/api/v1/summary
```

### 用 App 1.0 連線

Android 模擬器裡「設定」填 `http://10.0.2.2:8085`；實體手機填電腦的區網 IP，例如 `http://192.168.1.20:8085`。
App 1.0 連到本伺服器的清單畫面：`screenshots/v1-app1-list.png`（1.0 伺服器）、`screenshots/v2-app1-list.png`（用 1.0 的資料庫升級後的 2.0 伺服器）。
