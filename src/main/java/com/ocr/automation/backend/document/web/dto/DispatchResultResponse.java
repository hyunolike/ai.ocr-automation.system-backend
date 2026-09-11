package com.ocr.automation.backend.document.web.dto;

import com.ocr.automation.backend.document.service.DispatchResult;

/**
 * 대기 문서 접수 결과 응답.
 *
 * <p>처리 결과가 아니라 <b>접수</b> 결과다. 스케줄러는 이 값으로 큐가 붐비는지
 * (rejected > 0) 판단한다.
 */
public record DispatchResultResponse(int queued, int rejected, int skipped) {

    public static DispatchResultResponse from(DispatchResult result) {
        return new DispatchResultResponse(result.queued(), result.rejected(), result.skipped());
    }
}
