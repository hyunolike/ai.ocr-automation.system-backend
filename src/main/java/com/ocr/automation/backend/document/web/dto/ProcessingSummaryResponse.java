package com.ocr.automation.backend.document.web.dto;

import com.ocr.automation.backend.document.service.ProcessingSummary;

/** 배치 처리 결과 응답. 스케줄러가 받아서 로그로 남긴다. */
public record ProcessingSummaryResponse(int picked, int completed, int failed, int skipped) {

    public static ProcessingSummaryResponse from(ProcessingSummary summary) {
        return new ProcessingSummaryResponse(
                summary.picked(), summary.completed(), summary.failed(), summary.skipped());
    }
}
