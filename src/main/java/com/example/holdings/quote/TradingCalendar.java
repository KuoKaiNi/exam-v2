package com.example.holdings.quote;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * 依台北時間推算「現在應該要看得到哪一天的收盤價」。
 * 只知道週末不交易；國定假日不知道（官方 API 沒提供），由「當天 18:00 後還查不到就當休市」處理。
 */
public final class TradingCalendar {

    public static final LocalTime MARKET_CLOSE = LocalTime.of(13, 30);

    private TradingCalendar() {
    }

    /** now 必須已經是台北時間。13:30 以後算今天，之前算前一個平日；週末往前推到週五。 */
    public static LocalDate expectedLatestTradingDate(ZonedDateTime taipeiNow) {
        LocalDate d = taipeiNow.toLocalDate();
        if (taipeiNow.toLocalTime().isBefore(MARKET_CLOSE)) {
            d = d.minusDays(1);
        }
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
