package com.ocr.automation.backend.document.web;

import com.ocr.automation.backend.document.service.DocumentNotFoundException;
import com.ocr.automation.backend.document.service.InvalidDocumentException;
import com.ocr.automation.backend.document.web.dto.ErrorResponse;
import com.ocr.automation.backend.ocr.OcrEngineException;
import com.ocr.automation.backend.storage.DocumentStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 예외를 HTTP 응답으로 옮기는 곳.
 *
 * <p>서비스 계층은 도메인 언어로 예외를 던지고, HTTP 상태 코드 선택은 여기서만 한다.
 * 예상치 못한 예외는 원인을 서버 로그에만 남기고 클라이언트에는 일반 메시지를 준다.
 */
@Slf4j
@RestControllerAdvice
public class DocumentExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DOCUMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidDocumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_DOCUMENT", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("FILE_TOO_LARGE", "파일이 허용 크기를 초과했습니다"));
    }

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(DocumentStorageException e) {
        log.error("스토리지 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("STORAGE_ERROR", "문서 저장소 오류가 발생했습니다"));
    }

    @ExceptionHandler(OcrEngineException.class)
    public ResponseEntity<ErrorResponse> handleEngine(OcrEngineException e) {
        log.error("OCR 엔진 오류", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("OCR_ENGINE_ERROR", "OCR 엔진을 사용할 수 없습니다"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("INVALID_STATE", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
