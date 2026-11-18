# 持股記帳 API

兩組 API 同時提供，同一個資料庫：

| 版本 | 路徑 | 給誰用 | 說明 |
|---|---|---|---|
| 1.0 | `/api/v1` | 已發布的 App 1.0 | 規格不變，只看整張的部位。見附件《App 1.0 API 規格》 |
| 2.0 | `/api/v2` | App 2.0 | 股數用「股」，每檔可多次買賣，有冪等機制 |

伺服器預設在 `http://<主機>:8083`。App 的網址填 `http://10.0.2.2:8083`（模擬器）或電腦的區網 IP（實體手機）。

## 共通規則（2.0）

- 格式：JSON（UTF-8）。欄位名稱用小寫加底線。
- 股數（`shares`）：整數，1 到 999,999，單位是股。1 張 = 1,000 股，零股可以記。
- 價格（`price`、`buy_price`、`avg_price`）：數字，最多兩位小數。
- 金額（`amount`、`fee`、`tax`、`cost_basis`、`market_value`、`*_pnl`）：整數，單位是元。
- 日期：`YYYY-MM-DD`（西元年）。台北時間。
- 收益率（`*_return_pct`）：數字，單位 %，四捨五入到小數第二位。
- 取不到收盤價時，`current_price`、`price_date`、`market_value`、`unrealized_pnl`、`unrealized_return_pct` 為 `null`。
- 2.0 會忽略請求中不認得的欄位；回應會一直有下面列出的所有欄位，值可能是 `null`。

### 錯誤格式

所有錯誤（1.0 與 2.0）都是：

```json
{ "error": { "code": "INSUFFICIENT_SHARES", "message": "交易日 2026-11-11 賣出 2000 股，但當時只剩 1500 股" } }
```

`message` 是給使用者看的中文，可以直接顯示。

| HTTP | code | 何時 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 欄位缺少、型別不對、數值超出範圍、JSON 格式錯誤 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 2.0 的寫入缺少 `Idempotency-Key` 標頭 |
| 400 | `INSUFFICIENT_SHARES` | 賣出股數超過當時持有的股數（含日期順序造成的） |
| 404 | `NOT_FOUND` | 查無此持股或交易 |
| 404 | `STOCK_NOT_FOUND` | 新增時代號在上市、上櫃都查不到 |
| 409 | `DUPLICATE_CODE` | 新增時代號已在持股中（請改用新增買進） |
| 409 | `TRADE_CONFLICT` | 刪除這筆交易後，之後的賣出會超過持股 |
| 409 | `MULTI_TRADE`（1.0） | 1.0 的修改只能用在單一買進的持股 |
| 422 | `IDEMPOTENCY_KEY_REUSED` | 同一個 `Idempotency-Key` 拿來送不同的請求 |
| 503 | `MARKET_UNAVAILABLE` | 新增時官方行情連不上，資料不會存入 |

## 冪等（Idempotency-Key）— 2.0 的寫入必讀

手機網路不穩時，送出後 10 秒沒有回應，App 會把同一個請求再送一次。為了避免同一筆買進被記兩次：

- **所有 2.0 的 POST 與 DELETE 都必須帶 `Idempotency-Key` 標頭**，缺少回 400。
- 每一次「新的操作」用一個新的 key，建議用 UUID v4。
- **重送時，key 與請求內容（方法、路徑、JSON）都要一模一樣。**
  - 第一次成功的結果會被記下來。重送同一個 key 會得到相同的狀態碼與內容，不會再寫一次。
  - 同一個 key 拿來送不同內容，回 422 `IDEMPOTENCY_KEY_REUSED`。
- 驗證失敗（400、404、409 等）不會被記下來，修正後用同一個 key 重送會重新執行。
- key 長度上限 100 個字元。

建議 App 的做法：
1. 按下送出時產生 key，存在本機。
2. 10 秒沒有回應，用同一個 key、同一份內容重送。
3. 收到任何非網路錯誤的回應後，才清掉這個 key。
4. 只有「網路錯誤」才需要重送；收到 4xx 就顯示錯誤，不要自動重送。

同一台電腦或手機同時送出兩個不同的請求，伺服器會依序處理（鎖定同一個寫入點），不會出現兩筆都成功而超賣的情況。

## 持股部位（2.0）

```json
{
  "id": 1,
  "code": "2330",
  "name": "台積電",
  "market": "TWSE",
  "shares": 1500,
  "avg_price": 980.50,
  "avg_cost": 981.90,
  "cost_basis": 1472845,
  "realized_pnl": 554405,
  "current_price": 2175.00,
  "price_date": "2026-11-18",
  "market_value": 3262500,
  "unrealized_pnl": 1775219,
  "unrealized_return_pct": 120.53,
  "trades": [
    { "id": 1, "side": "BUY",  "trade_date": null,         "shares": 2000, "price": 980.50,
      "amount": 1961000, "fee": 2794, "tax": 0,    "realized_pnl": null },
    { "id": 5, "side": "SELL", "trade_date": "2026-11-10", "shares": 500,  "price": 2100.00,
      "amount": 1050000, "fee": 1496, "tax": 3150, "realized_pnl": 554405 }
  ]
}
```

| 欄位 | 說明 |
|---|---|
| `shares` | 目前剩餘股數。全部賣掉時為 0（部位保留，顯示已實現損益） |
| `avg_price` | 成交均價（不含手續費）＝剩餘買進金額 ÷ 剩餘股數 |
| `avg_cost` | 含費均價（含買進手續費）＝`cost_basis` ÷ 剩餘股數 |
| `cost_basis` | 剩餘股數的成本（買進金額＋買進手續費，依移動加權平均轉出） |
| `realized_pnl` | 已實現損益：所有賣出的（賣出金額 − 賣出手續費 − 證交稅 − 轉出的成本）加總 |
| `unrealized_pnl` | 未實現損益：剩餘股數以收盤價全部賣出後的淨額（扣賣出手續費與證交稅）− `cost_basis` |
| `unrealized_return_pct` | `unrealized_pnl` ÷ `cost_basis` × 100 |

`trades` 依交易日期排序（沒有日期的 1.0 買進排最前面）。`trade_date` 為 `null` 代表 1.0 升級來的買進，沒有記錄日期。

### 交易（trade）

| 欄位 | 說明 |
|---|---|
| `side` | `BUY` 或 `SELL` |
| `amount` | 成交金額 = 成交價 × 股數，四捨五入到元 |
| `fee` | 手續費 = 成交金額 × 0.1425%，無條件捨去，每筆最低 20 元 |
| `tax` | 證交稅（只有賣出）。一般股票 0.3%；ETF（00 開頭）0.1%；被動式債券 ETF（00 開頭、B 結尾）在免徵期間為 0。無條件捨去到元 |
| `realized_pnl` | 只有賣出才有值 |

### 總計（summary）

```json
{ "cost_basis": 2719619, "market_value": 4382730, "unrealized_pnl": 1643775,
  "unrealized_return_pct": 60.44, "realized_pnl": 554405, "quote_missing": 0 }
```

- `unrealized_pnl` 與 `market_value` 只算取得收盤價的部位；`cost_basis` 算全部。
- `quote_missing`：剩餘股數大於 0 但取不到收盤價的部位數。

## 端點（2.0）

| 方法 | 路徑 | 標頭 | 成功 | 說明 |
|---|---|---|---|---|
| GET | `/api/v2/holdings` | | 200，部位陣列 | 全部部位（含已全部賣掉的） |
| GET | `/api/v2/holdings/{id}` | | 200，部位 | 單一部位 |
| POST | `/api/v2/holdings` | `Idempotency-Key` | 201，部位 | 新增一檔（第一筆買進） |
| POST | `/api/v2/holdings/{id}/trades` | `Idempotency-Key` | 201，部位 | 新增買進或賣出，回傳更新後的部位 |
| DELETE | `/api/v2/holdings/{id}/trades/{tradeId}` | `Idempotency-Key` | 204 | 刪除一筆交易。刪完若之後的賣出會超過持股，回 409 |
| DELETE | `/api/v2/holdings/{id}` | `Idempotency-Key` | 204 | 刪除整檔（含所有交易） |
| GET | `/api/v2/summary` | | 200，總計 | |

### 新增一檔

`POST /api/v2/holdings`

```json
{ "code": "0050", "trade_date": "2026-11-02", "shares": 300, "price": 150 }
```

- `code`：4 到 6 碼數字，可加一個英文字母（例如 `00679B`）。會去證交所、櫃買中心查名稱與市場，查到收盤價一併存入。
- 代號已存在回 409 `DUPLICATE_CODE`。要加買進請用下面的端點。

### 新增買進或賣出

`POST /api/v2/holdings/{id}/trades`

```json
{ "side": "SELL", "trade_date": "2026-11-10", "shares": 500, "price": 2100 }
```

- `trade_date` 必填，不能是未來日期。
- 賣出超過當時持股回 400 `INSUFFICIENT_SHARES`。
- 日期會影響帳本順序：例如補記一筆較早的買進，系統會重新檢查之後的賣出是否仍成立。

### 刪除一筆交易

`DELETE /api/v2/holdings/{id}/trades/{tradeId}`，帶 `Idempotency-Key`，成功回 204（沒有內容）。

- 刪掉最後一筆交易時，整檔部位也會一起刪除。
- 修改交易的做法是：刪除後重新新增（2.0 沒有修改交易的端點）。

## 1.0 端點（維持不變）

| 方法 | 路徑 | 成功 | 說明 |
|---|---|---|---|
| GET | `/api/v1/holdings` | 200，陣列 | 只回整張（股數是 1000 的倍數）且還有股數的部位 |
| GET | `/api/v1/holdings/{id}` | 200 | 不是整張的部位回 404 `NOT_FOUND`，訊息提示用 2.0 |
| POST | `/api/v1/holdings` | 201 | `{"code","lots","buy_price"}`，建立第一筆買進（沒有日期） |
| PUT | `/api/v1/holdings/{id}` | 200 | `{"lots","buy_price"}`，只能用在單一買進的持股，否則 409 `MULTI_TRADE` |
| DELETE | `/api/v1/holdings/{id}` | 204 | 刪除整檔 |
| GET | `/api/v1/summary` | 200 | 只算上面那些整張的部位 |

1.0 的欄位對應：

- `lots` = 股數 ÷ 1000（整張）
- `buy_price` = 成交均價（`avg_price`）
- `pnl`、`return_pct` = `unrealized_pnl`、`unrealized_return_pct`
- `total_cost` = 剩餘成本（`cost_basis`）

1.0 沒有冪等機制。App 1.0 重送 POST 時會得到 409 `DUPLICATE_CODE`，但第一次已經成功，資料是正確的。
