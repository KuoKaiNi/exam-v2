# 從 1.0 升級到 2.0

上線方式：停掉 1.0 的伺服器，用**同一個資料庫**啟動 2.0。資料表由 Flyway 在 2.0 第一次啟動時自動改好，不用手動下 SQL。

## 升級步驟

以下指令在 repo 根目錄執行；資料庫位置照 1.0 的設定（預設 `data/holdings.mv.db`，有設 `DB_URL` 的話用同一個值）。

1. **停掉 1.0 的伺服器。** H2 檔案模式同時只能有一個程序開著，1.0 沒停，2.0 開不了資料庫。
   ```bash
   lsof -nP -iTCP:8085 -sTCP:LISTEN     # 找 PID
   kill <PID>
   ```
2. **備份資料庫檔。** 升級會刪掉舊欄位，退回 1.0 只能靠這份備份。
   ```bash
   cp data/holdings.mv.db data/holdings-1.0-backup-$(date +%Y%m%d%H%M).mv.db
   ```
3. **換成 2.0 的程式。**
   ```bash
   git fetch --tags && git checkout v2.0.0
   ```
4. **用同一個資料庫啟動 2.0。** 環境變數（`DB_URL`、`PORT`、`TWSE_BASE_URL`、`TPEX_BASE_URL`）照 1.0 的設定。
   ```bash
   ./mvnw spring-boot:run
   ```
   log 會出現：
   ```
   Migrating schema "PUBLIC" to version "3 - trades and idempotency"
   Successfully applied 1 migration to schema "PUBLIC", now at version v3
   ```
5. **驗證。**
   ```bash
   curl localhost:8085/api/v1/holdings   # 1.0 的持股都在，lots、buy_price 跟升級前一樣
   curl localhost:8085/api/v1/summary
   curl localhost:8085/api/v2/summary
   ```
   再用 App 1.0 連上去看清單（見 `screenshots/v2-app1-list.png`）。

實測：用 1.0（tag `v1.0.0`）建了四筆持股，包括修改過一次的，再照上面步驟升級。V3 套用約 20 毫秒；`/api/v1/holdings` 四筆的 `lots`、`buy_price`、`pnl`、`return_pct` 跟升級前 1.0 回的一樣。唯一的例外是 00679B：2.0 修正了債券 ETF 證交稅，盈虧從 −2,526 變成 −2,447（少扣 79 元稅）。

## 資料庫變動（`V3__trades_and_idempotency.sql`）

| 表 | 變動 |
|---|---|
| `holding` | 改成「部位」：一個代號一筆（`code` 唯一索引不變），**`id` 不變**（App 1.0 記住的 id 照常有效）。**移除 `lots`、`buy_price` 欄位**與它們的 CHECK；新增 `version`（樂觀鎖） |
| `trade`（新） | 每一筆買進或賣出：`holding_id`、`side`（BUY/SELL）、`trade_date`、`date_estimated`、`shares`（股）、`price`、`version`、建立／修改時間。外鍵連到 `holding`，刪部位時一起刪 |
| `idempotency_record`（新） | App 2.0 重送保護：Idempotency-Key、請求指紋、第一次的回應；保存 24 小時 |
| `stock_quote` | 不變（行情快取） |

**舊資料怎麼轉**：1.0 的每一筆持股轉成一筆買進，股數＝張數 × 1000，價格＝買進價。1.0 沒記買進日期，所以日期用該筆持股建立時間的**台北日期**，並標記 `date_estimated = TRUE`。網頁會提示使用者改成實際日期，App 2.0 可以看 `date_estimated` 提示。

**推估日期的副作用**：轉入的買進日期是「記進 1.0 的那天」，通常晚於實際買進日。使用者在 2.0 記一筆**早於那天**的賣出時，依日期重算會變成「先賣後買」，所以回 409 `INSUFFICIENT_SHARES`。錯誤訊息會提示「這檔有從 1.0 轉入的買進，日期是記錄當天…請先把那筆的日期改成實際買進日」。客服遇到「明明有股票卻不能賣」時，先請使用者改這個日期。

## 退回 1.0

**一定要還原備份，不能只把程式換回 1.0。** 實測：1.0 的程式開 2.0 的資料庫**會正常啟動**。Flyway 預設忽略比自己新的 migration（log 只寫 `Schema "PUBLIC" is up to date`），但 `lots`、`buy_price` 欄位已經刪了，所以首頁和每一個 `/api/v1` 都回 500（log：`Column "H1_0.BUY_PRICE" not found`）。服務看起來有起來，其實全部壞掉。

要退回：
1. 停掉 2.0。
2. 把第 2 步的備份檔複製回 `data/holdings.mv.db`。
3. `git checkout v1.0.0`，啟動。

**升級後才記的資料會不見**（例如零股、賣出），而且這些資料 1.0 也表達不了。所以退回要在上線後盡快決定；上線一段時間後就只能往前修，不退版。

## 上線後要注意

### App 1.0 使用者（預估半年後還有三成）

1.0 的 API 欄位、型別、狀態碼都沒變，但資料變豐富了，有幾種情況 1.0 的畫面表達不了（詳見 API.md 第 7 節）：

| 情況 | 1.0 看到的 | 客服可以這樣說明 |
|---|---|---|
| 在網頁或 2.0 記了零股，例如 1,500 股 | 張數 1（無條件捨去），盈虧是全部 1,500 股的 | 「1.0 只能顯示整張，零股有算進盈虧；請更新 App 看完整股數」 |
| 只有零股，例如 300 股 | 張數 0 | 同上 |
| 分次買進或賣出過的股票，在 1.0 按「修改」 | 錯誤訊息「有多筆買賣紀錄，1.0 版無法修改…」（HTTP 409 `HAS_TRADE_HISTORY`），資料不動 | 「請到網頁或更新 App 後修改」 |
| 全部賣光的股票 | 清單上不見了 | 「賣光的股票在網頁或 2.0 的『已全部賣出』裡，已實現損益也在那裡」 |
| 用 1.0 新增持股 | 會記成一筆今天的買進 | 日期可以到網頁改成實際買進日 |

建議看 log 裡 `HAS_TRADE_HISTORY` 出現的次數，判斷 1.0 使用者被擋的頻率，決定要不要提早催更新。

### 數字會變的地方

- **債券 ETF（00 開頭、B 結尾，例如 00679B）的盈虧會變好一點**：2.0 修正了證交稅（ISSUES.md 回報二），1.0 App 看到的數字也一起變。請先讓客服知道。
- **債券 ETF 停徵證交稅的期限是 2026-12-31**（現行條文）。延長到 2036 年底的修正案還在立法院。
  - 三讀公布後：改設定 `app.tax.bond-etf-exempt-until=2036-12-31`（或環境變數 `BOND_ETF_TAX_EXEMPT_UNTIL`），重啟即可。
  - 沒延長的話：2027-01-01 起程式自動照 0.1% 計算，不用改。
  - 修正案還把「主動式債券 ETF」納入停徵，那部分要改程式（`TaxRules` 加 D 結尾的規則）。

### App 2.0 串接

- 所有寫入都要帶 `Idempotency-Key`，沒帶會回 400。請 App 2.0 團隊照 API.md 第 1 節實作：每次操作一個新 key，自動重送沿用同一個。
- `idempotency_record` 每小時清掉 24 小時以前的資料，正常不會一直變大。

### 多台裝置

- 同一檔的寫入會鎖住那一列，一次只處理一個。資料庫連線字串預設加了 `LOCK_TIMEOUT=10000`（等鎖最多 10 秒）。**有自己設 `DB_URL` 的話，記得也加上 `;LOCK_TIMEOUT=10000`**。H2 2.3 預設只等 2 秒（實測 `SELECT LOCK_TIMEOUT()` 回 2000），同時操作時可能回 500。
- 只能跑**一個**伺服器程序（H2 檔案模式）。要多台機器分擔流量時，得換成 MySQL 或 PostgreSQL 並重新驗證鎖的行為。

### 行情

- 行情快取（`stock_quote`）、背景更新、官方網址設定都跟 1.0 相同，不用調整。
