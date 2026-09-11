package com.ocr.automation.backend.document.validation;

import com.ocr.automation.backend.document.service.InvalidDocumentException;

/**
 * 선언한 형식과 실제 내용이 맞지 않을 때.
 *
 * <p>{@link InvalidDocumentException} 의 하위 타입이라 잘못된 요청(400)으로 취급되지만,
 * 응답 코드는 따로 준다. "형식을 잘못 골랐다" 와 "형식을 속였다" 는 운영에서 구분해
 * 볼 가치가 있다 — 후자가 몰리면 공격 신호다.
 */
public class ContentMismatchException extends InvalidDocumentException {

    public ContentMismatchException(String message) {
        super(message);
    }
}
