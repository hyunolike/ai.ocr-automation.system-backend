package com.ocr.automation.backend.ocr;

/** OCR 엔진 수준의 실패. 재시도 대상이 된다. */
public class OcrEngineException extends RuntimeException {

    public OcrEngineException(String message) {
        super(message);
    }

    public OcrEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
