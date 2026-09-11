package com.ocr.automation.backend.document.web.dto;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;

import java.time.Instant;

/**
 * 문서 조회 응답. 도메인 엔티티를 그대로 노출하지 않는다.
 *
 * <p>추출 텍스트는 길 수 있으므로 여기에 싣지 않고
 * {@code GET /api/v1/documents/{publicId}/text} 로 따로 받는다.
 */
public record DocumentResponse(
        String id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int retryCount,
        String failureReason,
        Instant uploadedAt,
        Instant finishedAt,
        OcrResultSummary ocrResult) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getPublicId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getRetryCount(),
                document.getFailureReason(),
                document.getUploadedAt(),
                document.getFinishedAt(),
                OcrResultSummary.from(document));
    }

    /** 추출 결과 요약. 전체 텍스트 대신 길이만 준다. */
    public record OcrResultSummary(
            String engine,
            String language,
            Double confidence,
            Integer pageCount,
            int textLength,
            Long durationMillis,
            Instant processedAt) {

        static OcrResultSummary from(Document document) {
            if (!document.hasOcrResult()) {
                return null;
            }
            var result = document.getOcrResult();
            return new OcrResultSummary(
                    result.getEngine(),
                    result.getLanguage(),
                    result.getConfidence(),
                    result.getPageCount(),
                    result.textLength(),
                    result.getDurationMillis(),
                    result.getProcessedAt());
        }
    }
}
