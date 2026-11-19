# 持股記帳 API 2.0

給 App 2.0 團隊串接用。App 1.0 用的 `/api/v1` 不變，說明在最後一節。

- 路徑開頭 `/api/v2`，請求與回應都是 JSON（UTF-8），欄位小寫加底線。
- 股數單位是**股**（1 張 = 1,000 股），可以是零股。
- 金額（`amount`、`fee`、`total_cost`、`pnl` 類）是整數，單位元，四捨五入到元。
- 價格是數字，最多兩位小數。日期 `YYYY-MM-DD`（西元年，台北日期）。
- 規格上的欄位**一定會出現**；取不到的值是 `null`，不會省略。App 請忽略不認得的欄位，之後可能會加欄位。

## 目錄

1. [寫入請求一定要帶 Idempotency-Key](#1-寫入請求一定要帶-idempotency-key)
2. [錯誤格式](#2-錯誤格式)
3. [資料物件](#3-資料物件)
4. [API 清單](#4-api-清單)
5. [計算規則](#5-計算規則)
6. [多台裝置同時操作](#6-多台裝置同時操作)
7. [App 1.0 的 /api/v1 在 2.0 的行為](#7-app-10-的-apiv1-在-20-的行為)

---

## 1. 寫入請求一定要帶 Idempotency-Key

App 送出後 10 秒沒收到回應會自動重送。第一次的請求可能其實成功了，只是回應在路上掉了；沒有保護的話會記成兩筆買進。

**所有 POST、PUT、DELETE 都要帶標頭 `Idempotency-Key`**：

```
Idempotency-Key: 5f2c8a1e-7b0d-4c1e-9a55-0b8f3e2d4c61
```

- 值由 App 產生，建議用 UUID v4；8～100 個字元，只能用英數字和 `_ . : -`。
- **使用者每做一次操作產生一個新的 key；自動重送時沿用同一個 key，內容也要一模一樣。**
- 伺服器保存 key 24 小時。

| 情況 | 回應 |
|---|---|
| 第一次收到 | 正常執行，回正常結果 |
| 重送，第一次已經完成 | **不再執行**，回第一次的狀態碼與內容，加標頭 `Idempotent-Replayed: true`。第一次是 4xx 錯誤的話，重送也回同樣的錯誤 |
| 重送，第一次還在處理中 | `409 REQUEST_IN_PROGRESS`。等幾秒用**同一個 key** 再送 |
| 同一個 key、內容不同 | `422 IDEMPOTENCY_KEY_REUSED`（App 的 bug：新操作要用新 key） |
| 第一次是 503 或 500（暫時性錯誤） | 不保存；用同一個 key 重送會真的再執行一次 |
| 沒帶 key 或格式不對 | `400 VALIDATION_ERROR` |

伺服器處理一個請求最久約 9 秒：新代號第一次買進要向證交所／櫃買中心確認代號。所以 10 秒重送時，第一次通常已經完成。

## 2. 錯誤格式

跟 1.0 相同：

```json
{ "error": { "code": "INSUFFICIENT_SHARES", "message": "2330：2026-11-10 賣出 1500 股，超過當時持有的 1000 股" } }
```

`message` 是給使用者看的中文，可以直接顯示。

| HTTP | code | 什麼時候 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 欄位缺少或格式不對；沒帶 Idempotency-Key |
| 404 | `NOT_FOUND` | 部位或交易不存在 |
| 404 | `STOCK_NOT_FOUND` | 買進的代號在上市、上櫃都查不到 |
| 409 | `INSUFFICIENT_SHARES` | 賣出（或修改、刪除）之後，某一天的股數會小於 0 |
| 409 | `VERSION_CONFLICT` | 這筆交易已經被別台裝置改過或刪掉（見第 6 節） |
| 409 | `REQUEST_IN_PROGRESS` | 同一個 Idempotency-Key 的第一次請求還在處理 |
| 422 | `IDEMPOTENCY_KEY_REUSED` | 同一個 key 用在不同內容的請求 |
| 503 | `QUOTE_UNAVAILABLE` | 新代號要查證交所／櫃買中心，但官方網站連不上；資料沒有儲存，可以稍後重試 |
| 500 | `INTERNAL_ERROR` | 伺服器錯誤 |

## 3. 資料物件

### 部位（Holding）

一個股票代號一個部位。股數、成本、損益都由它底下的交易算出來。

```json
{
  "id": 1,
  "code": "2330",
  "name": "台積電",
  "market": "TWSE",
  "shares": 1200,
  "avg_cost": 1034.81,
  "total_cost": 1241766,
  "current_price": 1100.00,
  "price_date": "2026-11-12",
  "market_value": 1320000,
  "unrealized_pnl": 72393,
  "unrealized_return_pct": 5.83,
  "realized_pnl": 15112,
  "trades": [ ... ]
}
```

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | 整數 | 部位 id。跟 1.0 的持股 id 是同一個值 |
| `code` | 字串 | 股票代號 |
| `name` | 字串 | 股票名稱 |
| `market` | 字串 | `TWSE`（上市）或 `TPEX`（上櫃） |
| `shares` | 整數 | 目前股數；全部賣光是 0 |
| `avg_cost` | 數字或 `null` | 平均成本（**含買進手續費**），每股，四捨五入到小數第二位；股數 0 時為 `null` |
| `total_cost` | 整數 | 剩餘股數的成本（含買進手續費）；股數 0 時為 0 |
| `current_price` | 數字或 `null` | 官方最近一個交易日收盤價；未取得為 `null` |
| `price_date` | 字串或 `null` | 收盤價的日期 |
| `market_value` | 整數或 `null` | 收盤價 × 股數；未取得收盤價或股數 0 時為 `null` |
| `unrealized_pnl` | 整數或 `null` | 未實現損益：假設現在用收盤價把剩下的股數全部賣出的淨損益；同上為 `null` |
| `unrealized_return_pct` | 數字或 `null` | 未實現損益 ÷ total_cost × 100，四捨五入到小數第二位 |
| `realized_pnl` | 整數 | 這檔所有賣出的已實現損益合計；沒賣過是 0 |
| `trades` | 陣列 | **只有** `GET /api/v2/holdings/{id}` 才有；交易物件，新的在前 |

### 交易（Trade）

```json
{
  "id": 3,
  "holding_id": 1,
  "side": "SELL",
  "date": "2026-11-10",
  "date_estimated": false,
  "shares": 300,
  "price": 1090.00,
  "amount": 327000,
  "fee": 465,
  "tax": 981,
  "cost_basis": 310442,
  "realized_pnl": 15112,
  "shares_after": 1200,
  "version": 0
}
```

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | 整數 | 交易 id |
| `holding_id` | 整數 | 所屬部位 |
| `side` | 字串 | `BUY` 或 `SELL` |
| `date` | 字串 | 成交日期 |
| `date_estimated` | 布林 | `true` 表示日期不是使用者填的：1.0 沒記日期，用記錄當天代替。App 可以提示使用者改成實際日期 |
| `shares` | 整數 | 股數 |
| `price` | 數字 | 每股成交價 |
| `amount` | 整數 | 成交金額＝股數 × 價格 |
| `fee` | 整數 | 手續費 |
| `tax` | 整數 | 證交稅；買進是 0 |
| `cost_basis` | 整數或 `null` | 賣出的這些股數分到的成本；買進為 `null` |
| `realized_pnl` | 整數或 `null` | 這筆賣出的已實現損益；買進為 `null` |
| `shares_after` | 整數 | 依日期順序算到這筆之後的股數 |
| `version` | 整數 | 修改、刪除時帶回來，用來發現別台裝置的修改（第 6 節） |

### 總計（Summary）

```json
{
  "total_cost": 1241766,
  "market_value": 1320000,
  "unrealized_pnl": 72393,
  "unrealized_return_pct": 5.83,
  "realized_pnl": 15112,
  "quote_missing": 0,
  "open_positions": 1
}
```

| 欄位 | 說明 |
|---|---|
| `total_cost` | 還有股數的部位的 `total_cost` 加總 |
| `market_value` | 有收盤價的部位的 `market_value` 加總 |
| `unrealized_pnl` | 有收盤價的部位的 `unrealized_pnl` 加總 |
| `unrealized_return_pct` | `unrealized_pnl` ÷ `total_cost` × 100，四捨五入到小數第二位 |
| `realized_pnl` | 所有部位（含已賣光的）已實現損益加總 |
| `quote_missing` | 還有股數、但未取得收盤價的部位數；這些不算進 `market_value`、`unrealized_pnl`，但算進 `total_cost`（同 1.0） |
| `open_positions` | 還有股數的部位數 |

## 4. API 清單

| 方法 | 路徑 | 成功 | 可能的錯誤 |
|---|---|---|---|
| GET | `/api/v2/holdings` | 200，部位陣列（不含 `trades`） | |
| GET | `/api/v2/holdings?include_closed=true` | 200，含已全部賣光的部位 | |
| GET | `/api/v2/holdings/{id}` | 200，部位（含 `trades`） | 404 `NOT_FOUND` |
| DELETE | `/api/v2/holdings/{id}` | 204，刪除這檔與它的所有交易 | 404 |
| POST | `/api/v2/trades` | 201，`{trade, holding}` | 400、404 `STOCK_NOT_FOUND`、409 `INSUFFICIENT_SHARES`、503 |
| GET | `/api/v2/trades/{id}` | 200，交易 | 404 |
| PUT | `/api/v2/trades/{id}` | 200，`{trade, holding}` | 400、404、409 `INSUFFICIENT_SHARES`、409 `VERSION_CONFLICT` |
| DELETE | `/api/v2/trades/{id}?version=N` | 204 | 404、409 `INSUFFICIENT_SHARES`、409 `VERSION_CONFLICT` |
| GET | `/api/v2/summary` | 200，總計 | |

POST、PUT、DELETE 都要帶 `Idempotency-Key`（第 1 節），以下範例省略。

### 新增交易：`POST /api/v2/trades`

```json
{ "code": "2330", "side": "BUY", "date": "2026-11-02", "shares": 1500, "price": 1000 }
```

| 欄位 | 規則 |
|---|---|
| `code` | 必填，4～6 碼英數字（會轉大寫） |
| `side` | 必填，`BUY` 或 `SELL` |
| `date` | 必填，`YYYY-MM-DD`，不能晚於今天（台北） |
| `shares` | 必填，1～10,000,000 的整數（股） |
| `price` | 必填，大於 0，最多兩位小數 |

- 買進一個還沒有的代號：伺服器先向證交所／櫃買中心確認代號存在（最多約 9 秒），再建立部位。
- 賣出：這檔要有部位，而且依日期重算後每一天的股數都不能小於 0，否則 409 `INSUFFICIENT_SHARES`，資料不儲存。
- 同一檔再買進就是加到同一個部位，不會 409。

回應 201：

```json
{ "trade": { ...交易... }, "holding": { ...部位（不含 trades）... } }
```

### 修改交易：`PUT /api/v2/trades/{id}`

```json
{ "date": "2026-11-02", "shares": 2000, "price": 990.5, "version": 0 }
```

- `date`、`shares`、`price` 必填，規則同新增。
- `version`：建議帶上讀取時拿到的值。跟伺服器上的不同，表示別台裝置已經改過，回 409 `VERSION_CONFLICT`，資料不動。不帶就不檢查，直接覆蓋。
- 買賣方向不能改。要改請刪除再新增。帶了不同的 `side` 會回 400。
- 改完會依日期重算整個部位；會讓某一天賣超的修改回 409 `INSUFFICIENT_SHARES`，不儲存。

### 刪除交易：`DELETE /api/v2/trades/{id}?version=0`

- `version` 可省略，規則同修改。
- 刪掉買進會讓之後的賣出變成賣超時，回 409 `INSUFFICIENT_SHARES`。請先刪掉後面的賣出。
- 刪掉部位的最後一筆交易時，部位也一起刪除。

## 5. 計算規則

| 項目 | 規則 |
|---|---|
| 手續費 | 每一筆交易各自算：成交金額 × 0.1425%，無條件捨去到元，**每筆最低 20 元**（零股也一樣） |
| 證交稅 | 只有賣出收，無條件捨去到元。一般股票 0.3%；ETF（代號 00 開頭）0.1%；**債券 ETF（00 開頭、B 結尾）在 2017-01-01～2026-12-31 賣出免稅**（證券交易稅條例第 2-1 條，見 ISSUES.md 回報二）。稅率依賣出日期決定 |
| 交易順序 | 依日期；同一天先算買進再算賣出；同一天同方向依輸入順序 |
| 平均成本 | **移動平均法**。剩餘成本＝所有買進的（成交金額＋手續費）減掉已賣出部分的成本；平均成本＝剩餘成本 ÷ 剩餘股數 |
| 賣出的成本 | 剩餘成本 × 賣出股數 ÷ 賣出前股數；全部賣完時剛好等於剩餘成本。賣出不改變平均成本 |
| 已實現損益 | 每筆賣出：成交金額 − 手續費 − 證交稅 − 賣出的成本 |
| 未實現損益 | 假設今天用收盤價賣出剩下的股數：賣出金額 − 手續費 − 證交稅（依今天日期） − 剩餘成本 |
| 收益率 | 未實現損益 ÷ 剩餘成本 × 100，四捨五入到小數第二位 |

例子（價格是測試用）：1,000 股 @1,000 → 500 股 @1,100 → 賣 300 股 @1,090

| | 成交金額 | 手續費 | 證交稅 | 之後股數 | 剩餘成本 |
|---|---|---|---|---|---|
| 買 1,000 @1,000 | 1,000,000 | 1,425 | | 1,000 | 1,001,425 |
| 買 500 @1,100 | 550,000 | 783 | | 1,500 | 1,552,208（平均 1,034.81） |
| 賣 300 @1,090 | 327,000 | 465 | 981 | 1,200 | 1,552,208 − 310,441.6 ＝ 1,241,766 |

賣出的已實現損益 ＝ 327,000 − 465 − 981 − 310,441.6 ＝ 15,112.4 → **15,112**（四捨五入）。
剩下 1,200 股用收盤價 1,100 算未實現：1,320,000 − 手續費 1,881 − 證交稅 3,960 − 1,241,766.4 ＝ **72,393**，收益率 5.83%（就是上面部位範例的數字）。

## 6. 多台裝置同時操作

不少使用者手機和電腦同時開著。伺服器這樣處理：

- **同一檔的寫入一次只做一個**：每次新增、修改、刪除交易都先鎖住這檔的部位，用最新的全部交易重算後才存。手機和電腦同時賣出，第二個會看到第一個賣完後的股數；超過就 409 `INSUFFICIENT_SHARES`，不會賣超。
- **修改、刪除請帶 `version`**：使用者在手機上看到的資料可能已經被電腦改過。帶了 `version` 時伺服器會發現，回 409 `VERSION_CONFLICT`。App 收到後請重新讀取這檔（`GET /api/v2/holdings/{id}`），給使用者看最新內容再決定。
- **同時第一次買進同一個新代號**：只會建立一個部位，兩筆買進都記進去。
- 清單不會自動推播更新。App 回到前景或下拉重新整理時請重新讀取。

## 7. App 1.0 的 `/api/v1` 在 2.0 的行為

`/api/v1` 的路徑、欄位名稱、型別、狀態碼**都沒變**，App 1.0 照常可用。資料改成部位＋交易之後，對照方式如下：

| 1.0 欄位／操作 | 2.0 的對照 |
|---|---|
| `lots` | 股數 ÷ 1000，**無條件捨去**（整數；1,500 股顯示 1，300 股顯示 0） |
| `buy_price` | 平均買進價（不含手續費），每股 |
| `pnl`、`return_pct` | 整個部位（含零股）的未實現損益與收益率 |
| `GET /holdings` | 只列還有股數的部位；全部賣光的不列出 |
| `GET/PUT/DELETE /holdings/{id}` | 全部賣光的部位回 404 `NOT_FOUND` |
| `POST /holdings` | 記一筆今天的買進（`date_estimated: true`）。這檔已經有股數 → 409 `DUPLICATE_CODE`；這檔以前全部賣光了 → 在原部位再記一筆買進 |
| `PUT /holdings/{id}` | 這檔只有一筆買進時，改那一筆的股數（張數 × 1000）與價格；**有多筆交易時回 409 `HAS_TRADE_HISTORY`**，資料不動，message 請使用者到網頁或 2.0 修改 |
| `DELETE /holdings/{id}` | 刪除整個部位與所有交易 |
| `GET /summary` | 只算還有股數的部位；`pnl` 是未實現損益合計（不含已實現） |

債券 ETF 證交稅修正（ISSUES.md 回報二）後，1.0 看到的 00679B 等債券 ETF 盈虧也會自動更正。
