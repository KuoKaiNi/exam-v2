package com.example.holdings.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 測試用可調整的時鐘，固定台北時區。 */
public class MutableClock extends Clock {

    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private volatile Instant now;

    public MutableClock(String taipeiDateTime) {
        set(taipeiDateTime);
    }

    /** 例："2026-11-12T15:00" 台北時間。 */
    public void set(String taipeiDateTime) {
        now = java.time.LocalDateTime.parse(taipeiDateTime).atZone(TAIPEI).toInstant();
    }

    public ZonedDateTime taipeiNow() {
        return now.atZone(TAIPEI);
    }

    @Override
    public ZoneId getZone() {
        return TAIPEI;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
