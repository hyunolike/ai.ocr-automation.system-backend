package com.ocr.automation.backend.document.service;

/** 업로드 요청이 정책을 위반했을 때 (크기/형식/빈 파일 등). */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
