package com.ocr.automation.backend.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * OCR 처리 정책.
 *
 * @param maxRetries        이 횟수만큼 실패하면 FAILED 로 확정한다
 * @param staleAfterMinutes PROCESSING 상태가 이 시간을 넘기면 중단된 것으로 보고 회수한다
 * @param concurrency       동시에 돌릴 OCR 수. 0 이하면 CPU 코어 수를 쓴다
 * @param queueCapacity     워커 대기 큐 크기. 가득 차면 선점을 멈춘다(백프레셔)
 */
@ConfigurationProperties(prefix = "ocr.processing")
public record ProcessingProperties(
        @DefaultValue("3") int maxRetries,
        @DefaultValue("10") int staleAfterMinutes,
        @DefaultValue("0") int concurrency,
        @DefaultValue("50") int queueCapacity) {

    public Duration staleAfter() {
        return Duration.ofMinutes(staleAfterMinutes);
    }

    /**
     * 실제 동시 실행 수.
     *
     * <p>Tesseract 는 CPU 바운드라 코어보다 많이 띄워도 처리량이 늘지 않고 메모리만 먹는다.
     * 그래서 기본값을 코어 수로 둔다.
     */
    public int effectiveConcurrency() {
        return concurrency > 0 ? concurrency : Runtime.getRuntime().availableProcessors();
    }
}
