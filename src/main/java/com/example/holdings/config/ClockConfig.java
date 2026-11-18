package com.example.holdings.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全系統統一用台北時間。主機時區不是台北，所以不能用 Clock.systemDefaultZone()。
 */
@Configuration
public class ClockConfig {

    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Bean
    public Clock clock() {
        return Clock.system(TAIPEI);
    }
}
