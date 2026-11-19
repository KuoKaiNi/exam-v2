package com.example.holdings.pnl;

import java.time.LocalDate;

/** 依日期順序重算時，某一筆賣出超過當時手上的股數。 */
public class OversellException extends RuntimeException {

    private final LocalDate date;
    private final long sell;
    private final long held;

    public OversellException(LocalDate date, long sell, long held) {
        super(date + " 賣出 " + sell + " 股，超過當時持有的 " + held + " 股");
        this.date = date;
        this.sell = sell;
        this.held = held;
    }

    public LocalDate getDate() { return date; }
    public long getSell() { return sell; }
    public long getHeld() { return held; }
}
