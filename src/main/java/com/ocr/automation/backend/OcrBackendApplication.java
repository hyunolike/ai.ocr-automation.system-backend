package com.ocr.automation.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OCR 자동화 시스템 백엔드.
 *
 * <p>문서 업로드/조회 공개 API 와 OCR 처리 파이프라인을 담당한다.
 * "언제 처리할지"는 ocr-scheduler 가 정하고, 이 서비스는 "어떻게 처리할지"만 안다.
 */
@SpringBootApplication
public class OcrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrBackendApplication.class, args);
    }
}
