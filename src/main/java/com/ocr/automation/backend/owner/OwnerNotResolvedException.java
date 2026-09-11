package com.ocr.automation.backend.owner;

/** 요청에서 소유자를 특정하지 못했을 때. */
public class OwnerNotResolvedException extends RuntimeException {

    public OwnerNotResolvedException(String message) {
        super(message);
    }
}
