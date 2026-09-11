package com.ocr.automation.backend.document.service;

/**
 * 배치 처리 결과 요약. 스케줄러가 로그로 남기고 모니터링에 쓴다.
 *
 * @param picked    처리 대상으로 집어든 문서 수
 * @param completed OCR 성공 수
 * @param failed    OCR 실패 수 (재시도 예정 포함)
 * @param skipped   다른 인스턴스가 먼저 선점해 건너뛴 수
 */
public record ProcessingSummary(int picked, int completed, int failed, int skipped) {

    public static ProcessingSummary empty() {
        return new ProcessingSummary(0, 0, 0, 0);
    }
}
