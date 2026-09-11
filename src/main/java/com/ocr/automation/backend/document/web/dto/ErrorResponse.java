package com.ocr.automation.backend.document.web.dto;

import java.time.Instant;

/** 오류 응답 공통 형식. */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
