package com.ocr.automation.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * OCR 대상 문서. 상태 전이 규칙을 스스로 지킨다.
 *
 * <p>setter 를 두지 않고 의미 있는 메서드로만 상태를 바꾼다.
 * "PROCESSING 이 아닌데 완료 처리" 같은 잘못된 전이는 서비스가 아니라
 * 이 클래스가 막는다.
 */
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_status_uploaded_at", columnList = "status, uploaded_at"),
        @Index(name = "idx_documents_checksum", columnList = "checksum")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부에 노출하는 식별자. DB 채번 값을 그대로 드러내지 않는다 */
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256. 같은 파일이 중복 업로드되는지 판단하는 기준 */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    /** 스토리지 어댑터가 돌려준 키. 로컬 FS 면 상대 경로다 */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Embedded
    private OcrResult ocrResult;

    /** 스케줄러가 여러 인스턴스로 뜰 때 같은 문서를 두 번 잡는 것을 막는다 */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Document register(String originalFilename, String contentType,
                                    long sizeBytes, String checksum, String storageKey) {
        Document document = new Document();
        document.publicId = UUID.randomUUID().toString();
        document.originalFilename = originalFilename;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.checksum = checksum;
        document.storageKey = storageKey;
        document.status = DocumentStatus.PENDING;
        document.retryCount = 0;
        document.uploadedAt = Instant.now();
        return document;
    }

    /** PENDING 인 문서만 처리에 착수할 수 있다. */
    public void startProcessing() {
        if (status != DocumentStatus.PENDING) {
            throw new IllegalStateException(
                    "처리 대기 상태가 아닙니다: publicId=%s, status=%s".formatted(publicId, status));
        }
        this.status = DocumentStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
        this.failureReason = null;
    }

    public void completeWith(OcrResult result) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "처리 중인 문서가 아닙니다: publicId=%s, status=%s".formatted(publicId, status));
        }
        this.ocrResult = result;
        this.status = DocumentStatus.COMPLETED;
        this.finishedAt = Instant.now();
        this.failureReason = null;
    }

    /**
     * 처리 실패. 재시도 여유가 남아 있으면 PENDING 으로 되돌려
     * 다음 스케줄에 다시 집어가게 하고, 한도를 넘었으면 FAILED 로 확정한다.
     *
     * @return 재시도 대기 상태로 돌아갔으면 true
     */
    public boolean fail(String reason, int maxRetries) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "처리 중인 문서가 아닙니다: publicId=%s, status=%s".formatted(publicId, status));
        }
        this.retryCount++;
        this.failureReason = truncate(reason);
        if (retryCount < maxRetries) {
            this.status = DocumentStatus.PENDING;
            this.processingStartedAt = null;
            return true;
        }
        this.status = DocumentStatus.FAILED;
        this.finishedAt = Instant.now();
        return false;
    }

    /**
     * 선점을 되돌린다. 처리에 착수하지 못하고 반납하는 경우에만 쓴다.
     *
     * <p>{@link #fail} 과 달리 <b>재시도 횟수를 올리지 않는다.</b>
     * 워커 큐가 가득 차 작업을 받지 못한 것은 문서의 잘못이 아니므로,
     * 이것을 실패로 세면 큐가 붐빌 때마다 멀쩡한 문서가 FAILED 로 밀려난다.
     */
    public void releaseClaim() {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "처리 중인 문서가 아닙니다: publicId=%s, status=%s".formatted(publicId, status));
        }
        this.status = DocumentStatus.PENDING;
        this.processingStartedAt = null;
    }

    /**
     * 실패로 확정된 문서를 다시 처리 대기로 되돌린다.
     * 같은 파일을 다시 업로드했을 때 "이미 있는 문서"로 끝나지 않게 하기 위한 것이다.
     *
     * <p>재시도 횟수를 0 으로 되돌린다. 사용자의 재업로드는 새로운 시도이지
     * 이전 실패의 연장이 아니다.
     */
    public void resetForRetry() {
        if (status != DocumentStatus.FAILED) {
            throw new IllegalStateException(
                    "실패한 문서가 아닙니다: publicId=%s, status=%s".formatted(publicId, status));
        }
        this.status = DocumentStatus.PENDING;
        this.retryCount = 0;
        this.failureReason = null;
        this.processingStartedAt = null;
        this.finishedAt = null;
    }

    /**
     * 처리 도중 인스턴스가 죽어 PROCESSING 에 멈춘 문서를 회수한다.
     * 실패와 같은 규칙으로 재시도 여유를 판단한다.
     */
    public boolean recoverFromStall(int maxRetries) {
        return fail("처리 시간 초과로 회수됨", maxRetries);
    }

    /** 마지막으로 처리에 착수한 지 지정 시간을 넘겼는지. */
    public boolean isStalled(Duration threshold, Instant now) {
        return status == DocumentStatus.PROCESSING
                && processingStartedAt != null
                && processingStartedAt.isBefore(now.minus(threshold));
    }

    public boolean hasOcrResult() {
        return ocrResult != null && ocrResult.getProcessedAt() != null;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }
}
