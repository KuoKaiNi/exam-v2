-- 行情快取：每個股票代號一列。清單頁只讀這張表，不直接查官方網站。
CREATE TABLE stock_quote (
    code           VARCHAR(10)    NOT NULL PRIMARY KEY,
    market         VARCHAR(4)     NOT NULL,
    name           VARCHAR(60)    NOT NULL,
    close_price    DECIMAL(10, 2),
    price_date     DATE,
    checked_at     TIMESTAMP WITH TIME ZONE,
    last_error_at  TIMESTAMP WITH TIME ZONE,
    last_error     VARCHAR(300),
    CONSTRAINT ck_stock_quote_market CHECK (market IN ('TWSE', 'TPEX'))
);
