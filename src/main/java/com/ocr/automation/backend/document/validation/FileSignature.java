package com.ocr.automation.backend.document.validation;

import java.util.List;

/**
 * 형식별 매직 바이트.
 *
 * <p>파일 맨 앞 몇 바이트가 곧 형식의 지문이다. 확장자나 헤더와 달리 내용 자체에
 * 들어 있어 바꿔치기할 수 없다.
 */
enum FileSignature {

    PNG("image/png", List.of(
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})),

    JPEG("image/jpeg", List.of(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),

    /** 리틀엔디언(II)과 빅엔디언(MM) 두 가지가 있다. */
    TIFF("image/tiff", List.of(
            new byte[]{0x49, 0x49, 0x2A, 0x00},
            new byte[]{0x4D, 0x4D, 0x00, 0x2A})),

    /** "%PDF-" */
    PDF("application/pdf", List.of(
            new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}));

    private final String contentType;
    private final List<byte[]> magicBytes;

    FileSignature(String contentType, List<byte[]> magicBytes) {
        this.contentType = contentType;
        this.magicBytes = magicBytes;
    }

    String contentType() {
        return contentType;
    }

    static FileSignature of(String contentType) {
        for (FileSignature signature : values()) {
            if (signature.contentType.equalsIgnoreCase(contentType)) {
                return signature;
            }
        }
        return null;
    }

    boolean matches(byte[] content) {
        return magicBytes.stream().anyMatch(magic -> startsWith(content, magic));
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 내용이 실제로 어떤 형식인지. 아는 형식이 아니면 null. */
    static FileSignature detect(byte[] content) {
        for (FileSignature signature : values()) {
            if (signature.matches(content)) {
                return signature;
            }
        }
        return null;
    }
}
