package com.example.holdings.holding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByCode(String code);

    List<Holding> findAllByOrderByIdAsc();

    /**
     * 鎖住這個部位（SELECT ... FOR UPDATE）。同一檔的交易異動一次只會有一個在算，
     * 手機和電腦同時賣出時，第二個要等第一個存完、看到最新股數才檢查能不能賣。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.id = :id")
    Optional<Holding> lockById(@Param("id") long id);
}
