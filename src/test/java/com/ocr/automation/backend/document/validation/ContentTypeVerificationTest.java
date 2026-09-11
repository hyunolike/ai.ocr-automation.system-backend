package com.ocr.automation.backend.document.validation;

import com.ocr.automation.backend.document.SampleFiles;
import com.ocr.automation.backend.storage.StorageProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 형식 검증. 핵심은 <b>헤더가 아니라 내용을 본다</b>는 것이다.
 */
class ContentTypeVerificationTest {

    private static final int MAX_PDF_PAGES = 5;

    private final StorageProperties properties = new StorageProperties(
            "/tmp", 20971520L,
            List.of("image/png", "image/jpeg", "image/tiff", "application/pdf"),
            MAX_PDF_PAGES);

    private final ContentTypeVerifier verifier =
            new MagicBytesContentTypeVerifier(new PdfInspector(properties));

    private byte[] pdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    // ---------------- 정상 ----------------

    @Test
    void 실제_PNG_는_통과한다() {
        assertThatCode(() -> verifier.verify("image/png", SampleFiles.png("내용")))
                .doesNotThrowAnyException();
    }

    @Test
    void 실제_JPEG_는_통과한다() {
        assertThatCode(() -> verifier.verify("image/jpeg", SampleFiles.jpeg("내용")))
                .doesNotThrowAnyException();
    }

    @Test
    void 실제_TIFF_는_통과한다() {
        assertThatCode(() -> verifier.verify("image/tiff", SampleFiles.tiff("내용")))
                .doesNotThrowAnyException();
    }

    @Test
    void 빅엔디언_TIFF_도_통과한다() {
        byte[] bigEndian = {0x4D, 0x4D, 0x00, 0x2A, 0x01, 0x02};

        assertThatCode(() -> verifier.verify("image/tiff", bigEndian)).doesNotThrowAnyException();
    }

    @Test
    void 실제_PDF_는_통과한다() throws IOException {
        assertThatCode(() -> verifier.verify("application/pdf", pdf(1)))
                .doesNotThrowAnyException();
    }

    // ---------------- 형식을 속인 업로드 ----------------

    @Test
    void 확장자와_헤더만_바꾼_파일은_거부한다() {
        // 이것이 이 기능의 존재 이유다. 헤더만 믿으면 무엇이든 통과한다.
        byte[] notAnImage = "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify("image/png", notAnImage))
                .isInstanceOf(ContentMismatchException.class)
                .hasMessageContaining("image/png 형식이 아닙니다");
    }

    @Test
    void 다른_형식으로_위장하면_실제_형식을_알려준다() {
        assertThatThrownBy(() -> verifier.verify("image/png", SampleFiles.jpeg("사실은 JPEG")))
                .isInstanceOf(ContentMismatchException.class)
                .hasMessageContaining("실제: image/jpeg");
    }

    @Test
    void PDF_로_위장한_이미지는_거부한다() {
        assertThatThrownBy(() -> verifier.verify("application/pdf", SampleFiles.png("사실은 PNG")))
                .isInstanceOf(ContentMismatchException.class);
    }

    @Test
    void 시그니처보다_짧은_파일도_거부한다() {
        assertThatThrownBy(() -> verifier.verify("image/png", new byte[]{(byte) 0x89, 0x50}))
                .isInstanceOf(ContentMismatchException.class);
    }

    // ---------------- PDF 추가 검사 ----------------

    @Test
    void 페이지_수가_상한을_넘으면_거부한다() throws IOException {
        byte[] tooLong = pdf(MAX_PDF_PAGES + 1);

        assertThatThrownBy(() -> verifier.verify("application/pdf", tooLong))
                .isInstanceOf(ContentMismatchException.class)
                .hasMessageContaining("페이지가 너무 많습니다");
    }

    @Test
    void 상한과_같은_페이지_수는_통과한다() throws IOException {
        assertThatCode(() -> verifier.verify("application/pdf", pdf(MAX_PDF_PAGES)))
                .doesNotThrowAnyException();
    }

    @Test
    void 시그니처만_맞고_내용이_깨진_PDF_는_거부한다() {
        // %PDF- 로 시작하지만 실제로는 PDF 가 아니다.
        // 통과시키면 OCR 단계에서야 실패해 워커와 재시도를 낭비한다.
        byte[] broken = "%PDF-1.7\n이건 PDF 가 아니다".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify("application/pdf", broken))
                .isInstanceOf(ContentMismatchException.class)
                .hasMessageContaining("읽을 수 없습니다");
    }

    // ---------------- 목록 밖 형식 ----------------

    @Test
    void 검증할_수_없는_형식은_거부한다() {
        assertThatThrownBy(() -> verifier.verify("application/zip", SampleFiles.plain("내용")))
                .isInstanceOf(ContentMismatchException.class)
                .hasMessageContaining("검증할 수 없는 형식");
    }

    @Test
    void 허용_목록과_검증_가능한_형식이_어긋나지_않는다() {
        // 설정에 형식을 추가하고 FileSignature 에 넣는 것을 잊으면
        // 업로드가 전부 CONTENT_MISMATCH 로 떨어진다. 그 전에 여기서 걸린다.
        assertThat(properties.allowedContentTypes())
                .allSatisfy(contentType -> assertThat(FileSignature.of(contentType))
                        .as("허용 형식 %s 의 시그니처가 정의되어 있어야 한다", contentType)
                        .isNotNull());
    }
}
