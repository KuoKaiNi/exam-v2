package com.example.holdings.holding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByCode(String code);

    boolean existsByCode(String code);

    List<Holding> findAllByOrderByIdAsc();
}
