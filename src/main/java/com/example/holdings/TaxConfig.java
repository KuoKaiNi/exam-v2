package com.example.holdings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.holdings.pnl.TaxRules;

@Configuration
public class TaxConfig {

    @Bean
    public TaxRules taxRules() {
        return TaxRules.DEFAULT;
    }
}
