package com.ocr.automation.backend.security.apikey;

import com.ocr.automation.backend.owner.DocumentOwnerResolver;
import com.ocr.automation.backend.owner.OwnerNotResolvedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 인증된 API 키에서 소유자를 얻는다.
 *
 * <p>{@link com.ocr.automation.backend.security.ApiKeyAuthenticationFilter} 가 키를 검증하고
 * principal 에 소유자 식별자를 넣어둔 상태다. 여기서는 그것을 꺼내기만 한다.
 *
 * <p>이전의 헤더 기반 구현을 대체한다. 달라진 점은 <b>소유자를 증명하게 됐다는 것</b>이다.
 * 헤더는 누구나 주장할 수 있었지만, 키는 발급받은 사람만 제시할 수 있다.
 * 그 교체가 이 클래스 하나로 끝나는 것이 포트를 둔 이유다.
 */
@Component
public class ApiKeyDocumentOwnerResolver implements DocumentOwnerResolver {

    @Override
    public String currentOwnerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // 보안 설정이 /api/** 를 이미 막고 있으므로 정상 경로에서는 도달하지 않는다.
            // 설정이 잘못돼 인증 없이 통과하는 경우를 대비한 마지막 방어선이다.
            throw new OwnerNotResolvedException("인증되지 않은 요청입니다");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String ownerId) || ownerId.isBlank()) {
            throw new OwnerNotResolvedException("소유자를 확인할 수 없습니다");
        }
        return ownerId;
    }
}
