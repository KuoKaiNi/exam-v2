package com.example.holdings.service;

import com.example.holdings.domain.WriteLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 所有寫入都要先呼叫 acquire()，在同一個交易內拿到 write_lock 那一列的鎖，依序執行。 */
@Service
public class WriteLockService {

    private final WriteLockRepository repository;

    public WriteLockService(WriteLockRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        repository.lockById(1).orElseThrow(() -> new IllegalStateException("write_lock 沒有資料，請檢查 migration"));
    }
}
