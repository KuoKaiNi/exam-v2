package com.example.holdings.quote;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockQuoteRepository extends JpaRepository<StockQuote, String> {
}
