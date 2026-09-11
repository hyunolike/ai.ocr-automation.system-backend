package com.ocr.automation.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 문서 보관 설정.
 *
 * @param basePath            원본 문서를 저장할 루트 경로
 * @param maxFileSizeBytes    허용 최대 파일 크기
 * @param allowedContentTypes 허용 MIME 타입 목록
 * @param maxPdfPages         PDF 페이지 수 상한. 긴 문서 하나가 워커를 오래 점유하는 것을 막는다
 */
@ConfigurationProperties(prefix = "ocr.storage")
public record StorageProperties(
        @DefaultValue("./.local-storage/documents") String basePath,
        @DefaultValue("20971520") long maxFileSizeBytes,
        @DefaultValue({"image/png", "image/jpeg", "image/tiff", "application/pdf"})
        List<String> allowedContentTypes,
        @DefaultValue("200") int maxPdfPages) {
}
