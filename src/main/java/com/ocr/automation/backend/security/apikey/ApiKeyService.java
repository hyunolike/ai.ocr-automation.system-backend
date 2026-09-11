package com.ocr.automation.backend.security.apikey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * API 키 발급과 검증.
 *
 * <h2>왜 bcrypt 가 아니라 SHA-256 인가</h2>
 * bcrypt·argon2 같은 느린 해시는 <b>저엔트로피 비밀번호</b>를 무차별 대입에서
 * 지키기 위한 것이다. 여기서 다루는 키는 256비트 난수라 무차별 대입이 애초에
 * 불가능하므로 늘릴 이유가 없다. 오히려 <b>요청마다</b> 수행되는 검증에 느린 해시를
 * 쓰면 지연만 커진다.
 *
 * <p>대신 키 생성이 예측 불가능해야 한다는 조건이 핵심이다. 그래서
 * {@link SecureRandom} 을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    /** 키 앞에 붙는 표식. 유출된 문자열이 무엇인지 바로 알아볼 수 있게 한다. */
    private static final String KEY_PREFIX = "ocrk_";
    private static final int RANDOM_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ApiKeyRepository apiKeyRepository;

    /**
     * 새 키를 발급한다. 평문은 반환값에만 담기며 어디에도 저장되지 않는다.
     */
    @Transactional
    public IssuedApiKey issue(String ownerId, String label) {
        byte[] random = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(random);
        String plaintext = KEY_PREFIX + ENCODER.encodeToString(random);
        String displayPrefix = plaintext.substring(0, DISPLAY_PREFIX_LENGTH);

        ApiKey apiKey = ApiKey.issue(ownerId, label, displayPrefix, hash(plaintext));
        apiKeyRepository.save(apiKey);

        // 평문은 로그에 남기지 않는다. 접두사만 남겨 어느 키인지 추적할 수 있게 한다.
        log.info("API 키 발급: ownerId={}, prefix={}, label={}", ownerId, displayPrefix, label);
        return new IssuedApiKey(plaintext, displayPrefix, ownerId);
    }

    /**
     * 평문 키로 소유자를 알아낸다.
     *
     * @return 유효한 키면 소유자 식별자, 아니면 빈 값
     */
    @Transactional
    public Optional<String> resolveOwner(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }
        Optional<ApiKey> found = apiKeyRepository.findByKeyHash(hash(plaintext));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApiKey apiKey = found.get();
        if (!apiKey.isUsable()) {
            log.debug("사용할 수 없는 키입니다: prefix={}", apiKey.getKeyPrefix());
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (apiKey.needsTouch(now)) {
            apiKey.touch(now);
        }
        return Optional.of(apiKey.getOwnerId());
    }

    @Transactional(readOnly = true)
    public List<ApiKey> findByOwner(String ownerId) {
        return apiKeyRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public void revoke(String keyPrefix) {
        ApiKey apiKey = apiKeyRepository.findAll().stream()
                .filter(key -> key.getKeyPrefix().equals(keyPrefix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("키를 찾을 수 없습니다: " + keyPrefix));
        apiKey.revoke();
        log.info("API 키 폐기: ownerId={}, prefix={}", apiKey.getOwnerId(), keyPrefix);
    }

    private String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }
}
