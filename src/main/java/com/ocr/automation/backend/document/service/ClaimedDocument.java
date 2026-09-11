package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;

/**
 * 처리 대상으로 선점(claim)한 문서의 스냅샷.
 *
 * <p>OCR 은 트랜잭션 밖에서 오래 돌기 때문에 영속 엔티티를 그대로 들고 나가지 않고
 * 필요한 값만 복사해 넘긴다.
 */
public record ClaimedDocument(
        Long id,
        String publicId,
        String originalFilename,
        String contentType,
        String storageKey) {

    static ClaimedDocument from(Document document) {
        return new ClaimedDocument(
                document.getId(),
                document.getPublicId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getStorageKey());
    }
}
