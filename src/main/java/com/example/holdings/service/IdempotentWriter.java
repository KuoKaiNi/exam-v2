package com.example.holdings.service;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.IdempotencyRecord;
import com.example.holdings.domain.IdempotencyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 冪等寫入：同一個 Idempotency-Key 第二次送來時，直接回傳第一次的結果，不會重做。
 * 寫入與紀錄在同一個交易內，失敗（例如驗證錯誤）時兩者都不會留下。
 * 同一個 key 若拿來送不同的內容，回 422，避免誤用。
 */
@Service
public class IdempotentWriter {

    public record Stored(int status, String body) {
    }

    private final WriteLockService lock;
    private final IdempotencyRepository repository;
    private final Clock clock;

    public IdempotentWriter(WriteLockService lock, IdempotencyRepository repository, Clock clock) {
        this.lock = lock;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Stored run(String key, String fingerprint, Supplier<Stored> action) {
        lock.acquire();
        IdempotencyRecord previous = repository.findById(key).orElse(null);
        if (previous != null) {
            if (!previous.getFingerprint().equals(fingerprint)) {
                throw new HoldingException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                        "同一個 Idempotency-Key 不能用在不同的請求");
            }
            return new Stored(previous.getStatusCode(), previous.getResponseBody());
        }
        Stored result = action.get();
        repository.save(new IdempotencyRecord(key, fingerprint, result.status(), result.body(),
                LocalDateTime.now(clock)));
        return result;
    }
}
