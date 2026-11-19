package com.example.holdings.quote;

/** 上市（臺灣證券交易所）或上櫃（證券櫃檯買賣中心）。 */
public enum Market {
    TWSE("上市"), TPEX("上櫃");

    private final String label;

    Market(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
