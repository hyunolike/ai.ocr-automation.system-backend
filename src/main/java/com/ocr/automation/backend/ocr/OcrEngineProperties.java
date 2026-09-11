package com.ocr.automation.backend.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OCR 엔진 설정 (설정 서버의 ocr-backend.yml 에서 내려온다).
 *
 * @param type      사용할 엔진: tesseract | stub
 * @param tesseract Tesseract 전용 설정
 */
@ConfigurationProperties(prefix = "ocr.engine")
public record OcrEngineProperties(
        @DefaultValue("tesseract") String type,
        @DefaultValue Tesseract tesseract) {

    /**
     * @param dataPath             tessdata 디렉터리 경로
     * @param language             인식 언어 (예: kor+eng)
     * @param engineMode           0=Legacy, 1=LSTM, 2=Legacy+LSTM, 3=Default
     * @param pageSegmentationMode 페이지 분할 모드 (3=자동)
     */
    public record Tesseract(
            @DefaultValue("/usr/share/tesseract-ocr/5/tessdata") String dataPath,
            @DefaultValue("kor+eng") String language,
            @DefaultValue("1") int engineMode,
            @DefaultValue("3") int pageSegmentationMode) {
    }
}
