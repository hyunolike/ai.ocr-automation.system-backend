package com.ocr.automation.backend.document.service;

/** 요청한 문서가 없을 때. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String publicId) {
        super("문서를 찾을 수 없습니다: " + publicId);
    }
}
