package com.example.holdings.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    boolean existsByCode(String code);

    List<Holding> findAllByOrderByIdAsc();
}
