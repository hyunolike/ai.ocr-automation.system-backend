package com.ocr.automation.backend.ocr;

/**
 * OCR 엔진이 돌려주는 추출 결과.
 *
 * @param text       추출된 전체 텍스트
 * @param confidence 0.0 ~ 1.0. 엔진이 신뢰도를 주지 않으면 null
 * @param pageCount  처리한 페이지 수
 * @param language   인식에 사용한 언어 코드
 */
public record OcrExtraction(String text, Double confidence, Integer pageCount, String language) {

    public OcrExtraction {
        text = text == null ? "" : text;
    }
}
