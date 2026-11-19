package com.example.holdings.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TradingCalendarTest {

    @ParameterizedTest(name = "台北 {0} → 應看得到 {1}")
    @CsvSource({
            "2026-11-12T13:29, 2026-11-11",   // 週四收盤前：昨天
            "2026-11-12T13:30, 2026-11-12",   // 週四收盤後：今天
            "2026-11-12T23:59, 2026-11-12",
            "2026-11-13T09:00, 2026-11-12",   // 週五早上：週四
            "2026-11-14T15:00, 2026-11-13",   // 週六：週五
            "2026-11-16T08:00, 2026-11-13",   // 週一早上：上週五
            "2026-11-16T14:00, 2026-11-16"
    })
    void expectedDate(String taipei, String expected) {
        var now = LocalDateTime.parse(taipei).atZone(ZoneId.of("Asia/Taipei"));
        assertThat(TradingCalendar.expectedLatestTradingDate(now)).isEqualTo(LocalDate.parse(expected));
    }
}
