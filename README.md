# 持股記帳

記下自己買了哪些台股，用證交所／櫃買中心的官方收盤價，算出「如果現在全部賣掉，淨賺或淨虧多少」。

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
要清空資料：停掉伺服器後刪除 `data/` 資料夾。

## 畫面

首頁 http://localhost:8085/ 就是持股清單：上方新增，每一列可直接改張數、買進價後按「修改」，或按「刪除」。
同一個股票代號只能有一筆，重複新增會顯示「2330 已在持股中」，資料不儲存。

## 跑測試

```bash
./mvnw test
```
