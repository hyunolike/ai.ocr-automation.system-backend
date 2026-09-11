package com.ocr.automation.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCR 추출 결과. Document 에 embed 되는 값 객체다.
 *
 * <p>필드를 모두 래퍼 타입으로 둔 이유: 아직 처리되지 않은 문서에서
 * Hibernate 가 0 으로 채워진 빈 객체를 만들어내지 않게 하기 위함이다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcrResult {

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    /** 0.0 ~ 1.0. 엔진이 신뢰도를 주지 않으면 null */
    @Column(name = "confidence")
    private Double confidence;

    /** 결과를 만들어낸 엔진 식별자 (tesseract, stub ...) */
    @Column(name = "ocr_engine", length = 50)
    private String engine;

    @Column(name = "ocr_language", length = 50)
    private String language;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Column(name = "processed_at")
    private Instant processedAt;

    public static OcrResult of(String extractedText, Double confidence, String engine,
                               String language, Integer pageCount, Long durationMillis) {
        OcrResult result = new OcrResult();
        result.extractedText = extractedText == null ? "" : extractedText;
        result.confidence = confidence;
        result.engine = engine;
        result.language = language;
        result.pageCount = pageCount;
        result.durationMillis = durationMillis;
        result.processedAt = Instant.now();
        return result;
    }

    public int textLength() {
        return extractedText == null ? 0 : extractedText.length();
    }
}
