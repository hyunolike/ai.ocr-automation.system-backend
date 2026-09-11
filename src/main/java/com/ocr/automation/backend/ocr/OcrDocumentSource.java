package com.ocr.automation.backend.ocr;

/**
 * OCR 엔진에 넘기는 입력. 스토리지가 로컬이든 객체 스토리지든
 * 엔진은 바이트 배열만 보면 되도록 한 단계 끊어둔다.
 *
 * @param filename    원본 파일명 (확장자 판단용)
 * @param contentType MIME 타입
 * @param content     파일 내용
 */
public record OcrDocumentSource(String filename, String contentType, byte[] content) {

    public OcrDocumentSource {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("OCR 대상 내용이 비어 있습니다");
        }
    }

    public boolean isPdf() {
        return "application/pdf".equalsIgnoreCase(contentType);
    }

    public String extension() {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot);
    }
}
