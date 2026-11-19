package com.example.holdings.holding;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByHoldingId(Long holdingId);

    List<Trade> findByHoldingIdIn(Collection<Long> holdingIds);
}
