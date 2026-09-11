package com.ocr.automation.backend.ocr;

/**
 * OCR 엔진 포트.
 *
 * <p>비즈니스 로직은 이 인터페이스만 알고, 구체적인 엔진(Tesseract, 외부 API 등)은
 * 어댑터로 갈아 끼운다. 엔진을 교체해도 {@code DocumentProcessingService} 는
 * 그대로 재사용할 수 있어야 한다.
 */
public interface OcrEngine {

    /** 결과에 기록할 엔진 식별자 (예: tesseract). */
    String name();

    /**
     * 문서에서 텍스트를 추출한다.
     *
     * @throws OcrEngineException 엔진 수준의 실패 (네이티브 라이브러리 부재, 인식 불가 등)
     */
    OcrExtraction extract(OcrDocumentSource source);
}
