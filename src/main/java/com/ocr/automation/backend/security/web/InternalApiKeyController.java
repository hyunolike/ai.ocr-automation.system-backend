package com.ocr.automation.backend.security.web;

import com.ocr.automation.backend.security.apikey.ApiKey;
import com.ocr.automation.backend.security.apikey.ApiKeyService;
import com.ocr.automation.backend.security.apikey.IssuedApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * API 키 관리. <b>내부 경로</b>이므로 내부 토큰을 가진 호출자만 쓸 수 있다.
 *
 * <p>키 발급을 공개 API 에 둘 수는 없다 — 키를 받으려면 이미 키가 있어야 하는
 * 순환이 생긴다. 그래서 이미 보호되고 있는 내부 경로에 얹었다.
 *
 * <p>다만 이것은 <b>서비스 간 인증과 운영자 권한을 같은 토큰으로 묶은 것</b>이라
 * 엄밀하지 않다. 스케줄러가 쓰는 토큰으로 키도 발급할 수 있기 때문이다.
 * 실제 운영에서는 운영자 권한을 따로 두어야 한다.
 */
@RestController
@RequestMapping("/internal/v1/api-keys")
@RequiredArgsConstructor
public class InternalApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * 새 키를 발급한다.
     *
     * <p>응답의 {@code key} 는 <b>이 응답에서만</b> 볼 수 있다. 서버는 해시만 갖고 있어
     * 다시 보여줄 방법이 없다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueApiKeyResponse issue(@RequestBody IssueApiKeyRequest request) {
        IssuedApiKey issued = apiKeyService.issue(request.ownerId(), request.label());
        return new IssueApiKeyResponse(
                issued.plaintext(),
                issued.prefix(),
                issued.ownerId(),
                "이 키는 다시 보여줄 수 없습니다. 지금 안전한 곳에 보관하세요.");
    }

    @GetMapping
    public List<ApiKeySummary> list(@RequestParam String ownerId) {
        return apiKeyService.findByOwner(ownerId).stream().map(ApiKeySummary::from).toList();
    }

    @DeleteMapping("/{keyPrefix}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String keyPrefix) {
        apiKeyService.revoke(keyPrefix);
    }

    public record IssueApiKeyRequest(String ownerId, String label) {
    }

    public record IssueApiKeyResponse(String key, String prefix, String ownerId, String notice) {
    }

    /** 목록에는 평문도 해시도 담지 않는다. */
    public record ApiKeySummary(String prefix, String ownerId, String label,
                                boolean enabled, Instant createdAt, Instant lastUsedAt) {

        static ApiKeySummary from(ApiKey apiKey) {
            return new ApiKeySummary(
                    apiKey.getKeyPrefix(), apiKey.getOwnerId(), apiKey.getLabel(),
                    apiKey.isUsable(), apiKey.getCreatedAt(), apiKey.getLastUsedAt());
        }
    }
}
