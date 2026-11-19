package com.example.holdings.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.holdings.api.ApiError;
import com.example.holdings.api.ApiExceptionHandler;
import com.example.holdings.holding.HoldingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 重送保護（Idempotency-Key）。App 2.0 送出後 10 秒沒收到回應會自動再送一次同樣的請求，
 * 第一次其實可能已經成功（只是回應在路上掉了）。每個寫入請求帶一個 App 產生的唯一 key：
 * <ul>
 * <li>第一次：記下 key（PROCESSING）→ 執行 → 存下回應（DONE）。</li>
 * <li>重送（同 key、同內容）：不再執行，直接回第一次存下的回應，標頭 {@code Idempotent-Replayed: true}。</li>
 * <li>第一次還在處理中就收到重送：回 409 REQUEST_IN_PROGRESS（App 稍後再送同一個 key 即可）。</li>
 * <li>同 key 不同內容：422 IDEMPOTENCY_KEY_REUSED。</li>
 * <li>第一次是暫時性錯誤（503 官方連不上、500）：不存結果、刪掉 key，重送會真的再執行一次。</li>
 * </ul>
 * key 保存 24 小時。
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.:-]{8,100}$");
    private static final Duration KEEP = Duration.ofHours(24);
    /** PROCESSING 超過這麼久（伺服器中途當掉）就允許重送接手。 */
    private static final Duration STALE = Duration.ofSeconds(60);
    private static final MediaType JSON = MediaType.parseMediaType("application/json;charset=UTF-8");

    private final IdempotencyRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate newTx;
    private volatile Instant lastCleanup = Instant.EPOCH;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper mapper, Clock clock,
                              PlatformTransactionManager txManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param key         Idempotency-Key 標頭
     * @param fingerprint 方法＋路徑＋請求內容（見 {@link #fingerprint}）
     * @param action      真正要做的事；回傳成功時的狀態碼與內容（null 內容＝沒有內容）
     */
    public ResponseEntity<String> execute(String key, String fingerprint, Supplier<ResponseEntity<?>> action) {
        if (key == null || key.isBlank()) {
            throw HoldingException.validation("寫入請求要帶 " + HEADER + " 標頭（建議用 UUID），網路不穩重送時才不會重複記帳");
        }
        if (!KEY.matcher(key).matches()) {
            throw HoldingException.validation(HEADER + " 要是 8～100 個英數字或 _ . : -");
        }
        cleanupIfDue();
        Optional<ResponseEntity<String>> replay = begin(key, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        ResponseEntity<String> response;
        try {
            ResponseEntity<?> r = action.get();
            response = ResponseEntity.status(r.getStatusCode()).contentType(JSON).body(toJson(r.getBody()));
        } catch (HoldingException e) {
            HttpStatus status = ApiExceptionHandler.statusOf(e.getKind());
            if (status.is5xxServerError()) {
                abandon(key);
                throw e;
            }
            response = ResponseEntity.status(status).contentType(JSON)
                    .body(toJson(ApiError.of(e.getKind().name(), e.getMessage())));
        } catch (RuntimeException e) {
            abandon(key);
            throw e;
        }
        ResponseEntity<String> stored = response;
        newTx.executeWithoutResult(s -> repository.findById(key)
                .ifPresent(r -> r.complete(stored.getStatusCode().value(), stored.getBody())));
        return response;
    }

    private Optional<ResponseEntity<String>> begin(String key, String fingerprint) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return newTx.execute(s -> {
                    Optional<IdempotencyRecord> existing = repository.findById(key);
                    if (existing.isEmpty()) {
                        repository.saveAndFlush(new IdempotencyRecord(key, fingerprint, clock.instant()));
                        return Optional.<ResponseEntity<String>>empty();
                    }
                    IdempotencyRecord r = existing.get();
                    if (!r.getFingerprint().equals(fingerprint)) {
                        throw new HoldingException(HoldingException.Kind.IDEMPOTENCY_KEY_REUSED,
                                "這個 " + HEADER + " 已經用在另一個不同的請求，每個新請求要用新的 key");
                    }
                    if (IdempotencyRecord.DONE.equals(r.getStatus())) {
                        HttpHeaders h = new HttpHeaders();
                        h.add(REPLAYED_HEADER, "true");
                        return Optional.of(ResponseEntity.status(r.getResponseStatus()).headers(h).contentType(JSON)
                                .body(r.getResponseBody()));
                    }
                    if (Duration.between(r.getCreatedAt(), clock.instant()).compareTo(STALE) > 0) {
                        r.restart(clock.instant()); // 第一次處理到一半伺服器停了，由這次接手
                        return Optional.<ResponseEntity<String>>empty();
                    }
                    throw new HoldingException(HoldingException.Kind.REQUEST_IN_PROGRESS,
                            "同一個請求還在處理中，請稍候幾秒再試");
                });
            } catch (DataIntegrityViolationException e) {
                // 兩個相同 key 的請求同時到：讓第二個重新讀一次
            }
        }
        throw new HoldingException(HoldingException.Kind.REQUEST_IN_PROGRESS, "同一個請求還在處理中，請稍候幾秒再試");
    }

    private void abandon(String key) {
        newTx.executeWithoutResult(s -> repository.findById(key).ifPresent(repository::delete));
    }

    private void cleanupIfDue() {
        Instant now = clock.instant();
        if (Duration.between(lastCleanup, now).toMinutes() >= 60) {
            lastCleanup = now;
            newTx.executeWithoutResult(s -> repository.deleteOlderThan(now.minus(KEEP)));
        }
    }

    private String toJson(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String fingerprint(String method, String path, String body) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update((method + " " + path + "\n" + (body == null ? "" : body.strip())).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
