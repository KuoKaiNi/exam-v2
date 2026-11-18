-- 新增持股時向官方查得的股票名稱與市場（上市 TWSE／上櫃 TPEX）。
-- 既有列（只有第 2 題測試時的資料）補上預設值，正式新增的資料一律由官方查到。
ALTER TABLE holding ADD COLUMN name VARCHAR(50) DEFAULT '' NOT NULL;
ALTER TABLE holding ADD COLUMN market VARCHAR(10) DEFAULT 'TWSE' NOT NULL;
ALTER TABLE holding ADD CONSTRAINT ck_holding_market CHECK (market IN ('TWSE', 'TPEX'));
