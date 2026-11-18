package com.example.holdings.quote;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quote")
public record QuoteProperties(
        String twseBaseUrl,
        String tpexBaseUrl,
        Duration cacheTtl,
        Duration failureBackoff,
        Duration minRequestInterval,
        Duration connectTimeout,
        Duration readTimeout,
        Duration lookupDeadline) {
}
