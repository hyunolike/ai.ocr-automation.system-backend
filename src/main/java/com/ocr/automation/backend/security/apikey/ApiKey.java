package com.ocr.automation.backend.security.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 공개 API 호출자를 식별하는 키.
 *
 * <p><b>평문은 저장하지 않는다.</b> 발급 시 한 번만 보여주고 이후에는 해시로만 대조한다.
 * 문서의 카드번호 마스킹과 같은 원칙이다 — 원본을 갖고 있지 않으면 유출될 것도 없다.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_keys_owner_id", columnList = "owner_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    /** 마지막 사용 시각을 이 간격보다 자주 쓰지 않는다 (아래 {@link #touch} 참고). */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 식별용 앞부분(평문). 운영자가 어느 키인지 구분하는 용도이며 인증에는 쓰지 않는다. */
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    /** SHA-256 hex. 인증은 이 값의 일치로만 판단한다. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    /** 사람이 읽을 용도의 이름. "배치 연동용" 처럼. */
    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    static ApiKey issue(String ownerId, String label, String keyPrefix, String keyHash) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("소유자는 필수입니다");
        }
        ApiKey apiKey = new ApiKey();
        apiKey.ownerId = ownerId;
        apiKey.label = label;
        apiKey.keyPrefix = keyPrefix;
        apiKey.keyHash = keyHash;
        apiKey.enabled = true;
        apiKey.createdAt = Instant.now();
        return apiKey;
    }

    public boolean isUsable() {
        return enabled && revokedAt == null;
    }

    /**
     * 마지막 사용 시각을 갱신할 필요가 있는지.
     *
     * <p>요청마다 쓰면 읽기만 하는 호출도 전부 쓰기가 된다. 이 값은 "이 키가 아직
     * 쓰이고 있는가" 를 보려는 것이지 정밀한 접근 로그가 아니므로, 간격을 두고 갱신한다.
     */
    boolean needsTouch(Instant now) {
        return lastUsedAt == null || lastUsedAt.isBefore(now.minus(TOUCH_INTERVAL));
    }

    void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke() {
        if (revokedAt != null) {
            throw new IllegalStateException("이미 폐기된 키입니다: " + keyPrefix);
        }
        this.enabled = false;
        this.revokedAt = Instant.now();
    }
}
