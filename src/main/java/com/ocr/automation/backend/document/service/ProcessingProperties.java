package com.ocr.automation.backend.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * OCR 처리 정책.
 *
 * @param maxRetries        이 횟수만큼 실패하면 FAILED 로 확정한다
 * @param staleAfterMinutes PROCESSING 상태가 이 시간을 넘기면 중단된 것으로 보고 회수한다
 */
@ConfigurationProperties(prefix = "ocr.processing")
public record ProcessingProperties(
        @DefaultValue("3") int maxRetries,
        @DefaultValue("10") int staleAfterMinutes) {

    public Duration staleAfter() {
        return Duration.ofMinutes(staleAfterMinutes);
    }
}
