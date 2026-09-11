package com.ocr.automation.backend.document.domain;

/**
 * 문서 처리 상태.
 *
 * <pre>
 *   PENDING ──startProcessing──▶ PROCESSING ──completeWith──▶ COMPLETED
 *      ▲                             │
 *      │                             └──fail──▶ FAILED
 *      └────────────────────────────────────────┘
 *            재시도 여유가 남아 있으면 PENDING 으로 되돌아간다
 * </pre>
 */
public enum DocumentStatus {

    /** 업로드 완료, OCR 대기 */
    PENDING,

    /** OCR 처리 중 */
    PROCESSING,

    /** OCR 성공 */
    COMPLETED,

    /** OCR 실패, 재시도 한도 소진 */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
