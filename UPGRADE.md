# 從 1.0 升級到 2.0

## 結論先看

- 上線方式：停掉 1.0 伺服器，用**同一個資料庫檔**啟動 2.0。2.0 啟動時自動執行 `V3` migration。
- 1.0 的 API 路徑、欄位、狀態碼不變，已安裝 1.0 App 的使用者不用更新也能用，但有幾個差異（見「1.0 使用者會看到的變化」）。
- **migration 不能自動回退。** 升級前一定要備份資料庫檔。
- 同一個 H2 檔案不能被兩個程序同時開啟。1.0 與 2.0 不能同時跑。

## 升級前

1. 確認 `main` 與 `v2.0.0` tag 都已 push，並在要上線的機器上 `git checkout v2.0.0`。
2. 備份資料庫：停掉 1.0 伺服器後，複製整個 `data/` 資料夾（至少 `data/holdings.mv.db`）到別處。
3. 記下 1.0 的現況作為比對基準（建議在停止前做）：
   ```bash
   curl -s http://localhost:8083/api/v1/summary > before-summary.json
   curl -s http://localhost:8083/api/v1/holdings > before-list.json
   ```
4. 確認 2.0 的設定（見下面「設定」）。

## 升級步驟

1. 停掉 1.0 伺服器（用 `lsof -nP -iTCP:8083 -sTCP:LISTEN` 找 PID 再停止）。
2. 在 2.0 的目錄啟動，資料庫仍在 `./data/holdings`：
   ```bash
   ./mvnw spring-boot:run
   ```
3. 看啟動紀錄，確認 V3 執行成功：
   ```
   Migrating schema "PUBLIC" to version "3 - ledger shares and trades"
   Successfully applied 1 migration
   ```
4. 驗證 1.0 沒有變：
   ```bash
   curl -s http://localhost:8083/api/v1/summary > after-summary.json
   curl -s http://localhost:8083/api/v1/holdings > after-list.json
   diff <(python3 -m json.tool before-summary.json) <(python3 -m json.tool after-summary.json)
   diff <(python3 -m json.tool before-list.json) <(python3 -m json.tool after-list.json)
   ```
   兩個 diff 都應該沒有輸出。若有差異，不要讓使用者繼續用，先回退（見下面）。
   升級前的 1.0 資料只有整張、單一買進的持股，所以升級後 1.0 的輸出應該完全相同（用 v1.0.0 的測試資料驗證過）。
5. 驗證 2.0：
   ```bash
   curl -s http://localhost:8083/api/v2/holdings
   curl -s http://localhost:8083/api/v2/summary
   ```
6. 打開網頁 `http://localhost:8083/holdings`，確認清單與明細能開。

## 資料庫變動（V3）

| 變動 | 說明 |
|---|---|
| 新增 `trade` 表 | 每筆買進或賣出一列，含成交金額、手續費、證交稅（寫入時算好） |
| 1.0 持股 → 買進交易 | 每筆 1.0 持股搬成一列 `BUY`：股數 = 張數 × 1000，成交金額 = 買進價 × 股數（四捨五入到元），手續費同 1.0 規則，`trade_date` = NULL |
| 刪除 `holding.lots`、`holding.buy_price` | 股數與成本改由 `trade` 表算出。不可回退 |
| 新增 `write_lock` 表 | 只有一列，寫入時鎖定，讓多個請求依序執行 |
| 新增 `idempotency_key` 表 | 記錄 2.0 寫入的結果，供重送使用。目前沒有自動清理；若要清理，刪除 `created_at` 超過一年的列即可 |

`holding.id` 沒有改變，所以 App 1.0 的 `id` 仍然指向原本的持股。

## 設定

| 設定 | 位置 | 預設 | 說明 |
|---|---|---|---|
| 行情網址 | 環境變數 `QUOTE_TWSE_BASE_URL`、`QUOTE_TPEX_BASE_URL` | 官方網址 | 同 1.0 |
| 被動式債券 ETF 免徵截止日 | `application.yml` 的 `tax.bond-etf-exempt-until` | `2026-12-31` | **待確認**。修法定案後要改，見 ISSUES.md 第二則 |
| 連續查詢間隔、快取時間 | `application.yml` 的 `quote` | 1 秒、30 分鐘 | 同 1.0 |

## 1.0 使用者會看到的變化

1. **零股或部分賣出的部位不出現在 1.0。** 1.0 只能表示整張，所以 1.0 會把這些部位隱藏。使用者要到 2.0 查看。
2. **1.0 的「修改」只能用在單一買進的持股。** 多次買進的持股會回 409，訊息提示改用 2.0。
3. **被動式債券 ETF 的損益會變。** 免徵期間內賣出不再多扣 0.1% 證交稅，1.0 的 `pnl` 會跟著改。
4. **1.0 新增的持股沒有買進日期。** 這跟 1.0 原本的行為一樣。
5. **1.0 重送 POST 會得到 409。** 第一次其實已經成功，請客服告訴使用者不用擔心。

## 回退

2.0 的 migration 會刪除 `holding.lots` 與 `holding.buy_price`，不能直接回到 1.0 的資料庫結構。回退步驟：

1. 停掉 2.0 伺服器。
2. 把升級前備份的 `data/` 還原回去。
3. 用 `v1.0.0` tag 啟動 1.0：`git checkout v1.0.0 && ./mvnw spring-boot:run`。

回退會丟掉升級後新增的資料（賣出紀錄、新增的持股）。要回退前，先評估這些資料的影響。

## 上線後要注意

- **免徵截止日：** 到 2026-12-31 前，確認財政部或證交所的修法結果，更新 `tax.bond-etf-exempt-until`，並重新啟動。
- **官方行情連不上：** 清單顯示「未取得」、新增回 503。若長時間出現，檢查 `QUOTE_*_BASE_URL` 與網路。
- **單一程序：** 目前只能有一個伺服器程序開啟資料庫。要水平擴充前，需要先把資料庫換成 MySQL 或 PostgreSQL。
- **App 2.0 的重送：** 寫入請求都要帶 `Idempotency-Key`。若 App 漏帶，會直接收到 400，不會默默寫入。
