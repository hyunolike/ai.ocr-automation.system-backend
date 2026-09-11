package com.ocr.automation.backend.security;

import com.ocr.automation.backend.security.apikey.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 테스트에서 인증된 요청을 만드는 도우미.
 *
 * <p>키를 직접 만들지 않고 실제 발급 경로({@link ApiKeyService#issue})를 쓴다.
 * 해시 저장이나 검증이 깨지면 테스트가 함께 깨져야 하기 때문이다.
 */
@Component
public class AuthenticatedRequests {

    @Autowired
    private ApiKeyService apiKeyService;

    @Value("${ocr.security.internal-token}")
    private String internalToken;

    /** 이 소유자로 쓸 수 있는 새 API 키를 발급한다. */
    public String issueKeyFor(String ownerId) {
        return apiKeyService.issue(ownerId, "테스트용").plaintext();
    }

    public MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder builder, String apiKey) {
        return builder.header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey);
    }

    public MockHttpServletRequestBuilder asInternal(MockHttpServletRequestBuilder builder) {
        return builder.header(InternalTokenAuthenticationFilter.INTERNAL_TOKEN_HEADER, internalToken);
    }
}
