package com.example.holdings.quote;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 官方「個股日成交資訊」一個月份的內容。name 可能是空字串（櫃買中心查不到代號時）。
 */
public record MonthlyQuote(String name, List<DailyClose> days) {

    /** 這個月最後一個有成交（有收盤價）的交易日。 */
    public Optional<DailyClose> latest() {
        return days.stream().max(Comparator.comparing(DailyClose::date));
    }
}
