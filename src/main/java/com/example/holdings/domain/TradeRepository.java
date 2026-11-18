package com.example.holdings.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByHoldingId(Long holdingId);
}
