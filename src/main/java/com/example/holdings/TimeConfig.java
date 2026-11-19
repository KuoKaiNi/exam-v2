package com.example.holdings;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 正式機在海外、主機時區不是台北，所以程式裡所有「現在幾點、今天幾號」都從這個 Clock 取，
 * 不依賴 JVM 或作業系統的預設時區。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Taipei}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
