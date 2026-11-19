package com.example.holdings;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.holdings.pnl.TaxRules;

@Configuration
public class TaxConfig {

    @Bean
    public TaxRules taxRules(@Value("${app.tax.bond-etf-exempt-until:2026-12-31}") String exemptUntil) {
        return new TaxRules(LocalDate.parse(exemptUntil));
    }
}
