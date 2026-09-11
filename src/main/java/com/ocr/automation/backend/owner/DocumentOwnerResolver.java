package com.ocr.automation.backend.owner;

/**
 * 지금 요청이 누구의 것인지 알려주는 포트.
 *
 * <p>컨트롤러는 소유자를 <b>파라미터로 받지 않고</b> 이 포트에 묻는다.
 * 클라이언트가 보낸 값을 그대로 믿으면 헤더 하나로 남의 문서를 조회할 수 있기 때문이다.
 *
 * <p>인증(Phase 1.3)이 들어오면 API Key 를 해석하는 구현으로 바꿔 끼운다.
 * 그때 바뀌는 것은 이 인터페이스의 구현체 하나뿐이고,
 * 서비스와 도메인은 손대지 않는다.
 */
public interface DocumentOwnerResolver {

    /**
     * 현재 요청의 소유자 식별자.
     *
     * @throws OwnerNotResolvedException 소유자를 특정할 수 없을 때
     */
    String currentOwnerId();
}
