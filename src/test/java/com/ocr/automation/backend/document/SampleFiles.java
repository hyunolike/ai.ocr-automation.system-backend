package com.ocr.automation.backend.document;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 테스트용 파일 바이트.
 *
 * <p>업로드 검증이 매직 바이트를 보므로 실제 시그니처로 시작해야 한다.
 * 뒤에 붙는 본문은 문서마다 다르게 해 체크섬이 겹치지 않게 하는 용도다.
 */
public final class SampleFiles {

    public static final byte[] PNG_MAGIC =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    public static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    public static final byte[] TIFF_MAGIC = {0x49, 0x49, 0x2A, 0x00};

    private SampleFiles() {
    }

    public static byte[] png(String body) {
        return withMagic(PNG_MAGIC, body);
    }

    public static byte[] jpeg(String body) {
        return withMagic(JPEG_MAGIC, body);
    }

    public static byte[] tiff(String body) {
        return withMagic(TIFF_MAGIC, body);
    }

    /** 시그니처 없이 본문만. 형식을 속인 업로드를 만들 때 쓴다. */
    public static byte[] plain(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withMagic(byte[] magic, String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(magic);
        out.writeBytes(body.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
