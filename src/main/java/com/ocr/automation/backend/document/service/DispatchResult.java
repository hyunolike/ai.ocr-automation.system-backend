package com.ocr.automation.backend.document.service;

/**
 * 대기 문서 접수 결과.
 *
 * <p>이것은 <b>접수</b> 결과이지 처리 결과가 아니다. {@code queued} 는 워커 풀이
 * 받아들인 수이고, 실제 성공·실패는 나중에 문서 상태로 확인한다.
 *
 * @param queued   워커 큐에 넣은 문서 수
 * @param rejected 큐가 가득 차 넣지 못한 문서 수 (다음 주기에 다시 시도한다)
 * @param skipped  다른 인스턴스가 먼저 선점해 건너뛴 수
 */
public record DispatchResult(int queued, int rejected, int skipped) {

    public static DispatchResult empty() {
        return new DispatchResult(0, 0, 0);
    }

    public boolean didNothing() {
        return queued == 0 && rejected == 0;
    }
}
