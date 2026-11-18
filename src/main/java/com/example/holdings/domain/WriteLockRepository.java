package com.example.holdings.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WriteLockRepository extends JpaRepository<WriteLock, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WriteLock w where w.id = :id")
    Optional<WriteLock> lockById(@Param("id") Integer id);
}
