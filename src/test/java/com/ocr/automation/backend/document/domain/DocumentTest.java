package com.ocr.automation.backend.document.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상태 전이 규칙은 도메인이 지킨다. 서비스 계층을 거치지 않고 직접 검증한다.
 */
class DocumentTest {

    private static final int MAX_RETRIES = 3;

    private Document newDocument() {
        return Document.register("scan.png", "image/png", 1024L, "checksum", "2026/09/11/key.png");
    }

    private OcrResult sampleResult() {
        return OcrResult.of("추출된 텍스트", 0.9, "stub", "kor+eng", 1, 12L);
    }

    @Test
    void 등록하면_대기_상태가_된다() {
        Document document = newDocument();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getRetryCount()).isZero();
        assertThat(document.getPublicId()).isNotBlank();
        assertThat(document.hasOcrResult()).isFalse();
    }

    @Test
    void 대기에서_처리_거쳐_완료된다() {
        Document document = newDocument();

        document.startProcessing();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.getProcessingStartedAt()).isNotNull();

        document.completeWith(sampleResult());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.hasOcrResult()).isTrue();
        assertThat(document.getOcrResult().getExtractedText()).isEqualTo("추출된 텍스트");
        assertThat(document.getFinishedAt()).isNotNull();
    }

    @Test
    void 처리중이_아닌_문서는_완료할_수_없다() {
        Document document = newDocument();

        assertThatThrownBy(() -> document.completeWith(sampleResult()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("처리 중인 문서가 아닙니다");
    }

    @Test
    void 대기중이_아닌_문서는_다시_처리에_착수할_수_없다() {
        Document document = newDocument();
        document.startProcessing();

        assertThatThrownBy(document::startProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("처리 대기 상태가 아닙니다");
    }

    @Test
    void 재시도_여유가_남으면_대기로_돌아간다() {
        Document document = newDocument();
        document.startProcessing();

        boolean willRetry = document.fail("엔진 오류", MAX_RETRIES);

        assertThat(willRetry).isTrue();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getRetryCount()).isEqualTo(1);
        assertThat(document.getProcessingStartedAt()).isNull();
        assertThat(document.getFailureReason()).isEqualTo("엔진 오류");
    }

    @Test
    void 재시도_한도를_넘으면_실패로_확정된다() {
        Document document = newDocument();

        for (int attempt = 1; attempt < MAX_RETRIES; attempt++) {
            document.startProcessing();
            assertThat(document.fail("엔진 오류", MAX_RETRIES)).isTrue();
        }

        document.startProcessing();
        boolean willRetry = document.fail("엔진 오류", MAX_RETRIES);

        assertThat(willRetry).isFalse();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getRetryCount()).isEqualTo(MAX_RETRIES);
        assertThat(document.getStatus().isTerminal()).isTrue();
    }

    @Test
    void 재처리에_성공하면_실패_사유가_지워진다() {
        Document document = newDocument();
        document.startProcessing();
        document.fail("일시적 오류", MAX_RETRIES);

        document.startProcessing();
        document.completeWith(sampleResult());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getFailureReason()).isNull();
    }

    @Test
    void 처리_시작_후_기준_시간이_지나면_정체로_판정한다() {
        Document document = newDocument();
        document.startProcessing();

        Instant muchLater = Instant.now().plus(Duration.ofMinutes(30));

        assertThat(document.isStalled(Duration.ofMinutes(10), muchLater)).isTrue();
        assertThat(document.isStalled(Duration.ofMinutes(10), Instant.now())).isFalse();
    }

    @Test
    void 대기중인_문서는_정체로_보지_않는다() {
        Document document = newDocument();

        assertThat(document.isStalled(Duration.ofMinutes(10), Instant.now().plus(Duration.ofDays(1))))
                .isFalse();
    }

    @Test
    void 정체된_문서를_회수하면_재시도_대기로_돌아간다() {
        Document document = newDocument();
        document.startProcessing();

        boolean willRetry = document.recoverFromStall(MAX_RETRIES);

        assertThat(willRetry).isTrue();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getFailureReason()).contains("처리 시간 초과");
    }

    @Test
    void 실패_사유가_아주_길면_잘라서_보관한다() {
        Document document = newDocument();
        document.startProcessing();

        document.fail("x".repeat(5000), MAX_RETRIES);

        assertThat(document.getFailureReason()).hasSize(1000);
    }
}
