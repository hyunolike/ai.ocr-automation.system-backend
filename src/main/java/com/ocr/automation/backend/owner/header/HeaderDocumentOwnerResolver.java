package com.ocr.automation.backend.owner.header;

import com.ocr.automation.backend.owner.DocumentOwnerResolver;
import com.ocr.automation.backend.owner.OwnerNotResolvedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 요청 헤더에서 소유자를 읽는 <b>임시</b> 구현.
 *
 * <h2>⚠️ 이것은 인증이 아니다</h2>
 * 헤더는 누구나 마음대로 보낼 수 있다. 이 구현은 소유자 <b>격리</b>를 먼저 세우기 위한
 * 자리표시자일 뿐이며, 호출자가 자기가 주장하는 사람이 맞는지는 전혀 검증하지 않는다.
 *
 * <p>Phase 1.3 에서 API Key 를 해석하는 구현으로 교체하고 이 클래스는 지운다.
 * 그전까지 이 서비스를 신뢰할 수 없는 네트워크에 노출해서는 안 된다.
 *
 * <h2>기본값을 두지 않는 이유</h2>
 * 헤더가 없을 때 "기본 소유자"로 떨어지게 만들면, 인증을 붙이지 않은 채로도
 * 시스템이 그럭저럭 돌아가 보인다. 그 상태가 운영까지 따라가기 쉽다.
 * 그래서 헤더가 없으면 그냥 거절한다.
 */
@Component
@RequiredArgsConstructor
public class HeaderDocumentOwnerResolver implements DocumentOwnerResolver {

    public static final String OWNER_HEADER = "X-Owner-Id";

    private static final int MAX_LENGTH = 64;

    private final HttpServletRequest request;

    @Override
    public String currentOwnerId() {
        String ownerId = request.getHeader(OWNER_HEADER);
        if (!StringUtils.hasText(ownerId)) {
            throw new OwnerNotResolvedException(
                    "%s 헤더가 필요합니다".formatted(OWNER_HEADER));
        }
        String trimmed = ownerId.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new OwnerNotResolvedException(
                    "소유자 식별자가 너무 깁니다: 최대 %d자".formatted(MAX_LENGTH));
        }
        return trimmed;
    }
}
