package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.storage.DocumentStorage;
import com.ocr.automation.backend.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 문서 등록/조회 비즈니스 로직.
 *
 * <p>HTTP 도 OCR 엔진도 모르는 계층이다. REST 를 다른 프로토콜로 바꿔도
 * 이 클래스는 그대로 재사용할 수 있어야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final StorageProperties storageProperties;

    /**
     * 문서를 저장하고 OCR 대기열(PENDING)에 올린다.
     *
     * <p>같은 내용(체크섬 일치)의 문서가 이미 있으면 새로 저장하지 않고 기존 문서를
     * 돌려준다. 스토리지에 같은 파일이 쌓이는 것을 막는다.
     *
     * <p>단 기존 문서가 {@code FAILED} 라면 다시 대기열에 올린다. 그러지 않으면
     * 사용자가 실패한 문서를 다시 올려도 아무 일도 일어나지 않는다.
     */
    @Transactional
    public Document upload(String originalFilename, String contentType, byte[] content) {
        validate(originalFilename, contentType, content);

        String checksum = sha256(content);
        Optional<Document> existing = documentRepository.findFirstByChecksumOrderByUploadedAtDesc(checksum);
        if (existing.isPresent()) {
            return reuse(existing.get(), checksum);
        }

        String storageKey = documentStorage.store(originalFilename, content);
        Document document = Document.register(
                originalFilename, contentType, content.length, checksum, storageKey);
        Document saved = documentRepository.save(document);
        log.info("문서 등록: publicId={}, filename={}, size={}",
                saved.getPublicId(), originalFilename, content.length);
        return saved;
    }

    /**
     * 이미 등록된 같은 내용의 문서를 재사용한다.
     * 실패로 끝난 문서였다면 재처리 대기로 되돌린다.
     */
    private Document reuse(Document existing, String checksum) {
        if (existing.getStatus() == DocumentStatus.FAILED) {
            existing.resetForRetry();
            log.info("실패했던 문서를 다시 대기열에 올립니다: publicId={}, checksum={}",
                    existing.getPublicId(), checksum);
            return existing;
        }
        log.info("동일한 문서가 이미 등록되어 있습니다: publicId={}, status={}, checksum={}",
                existing.getPublicId(), existing.getStatus(), checksum);
        return existing;
    }

    @Transactional(readOnly = true)
    public Document findByPublicId(String publicId) {
        return documentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new DocumentNotFoundException(publicId));
    }

    @Transactional(readOnly = true)
    public Page<Document> findAll(DocumentStatus status, Pageable pageable) {
        return status == null
                ? documentRepository.findAll(pageable)
                : documentRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public String extractedTextOf(String publicId) {
        Document document = findByPublicId(publicId);
        if (!document.hasOcrResult()) {
            throw new InvalidDocumentException(
                    "아직 OCR 결과가 없습니다: publicId=%s, status=%s"
                            .formatted(publicId, document.getStatus()));
        }
        return document.getOcrResult().getExtractedText();
    }

    private void validate(String originalFilename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidDocumentException("빈 파일은 업로드할 수 없습니다");
        }
        if (content.length > storageProperties.maxFileSizeBytes()) {
            throw new InvalidDocumentException(
                    "파일이 너무 큽니다: %d bytes (최대 %d bytes)"
                            .formatted(content.length, storageProperties.maxFileSizeBytes()));
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidDocumentException("파일명은 필수입니다");
        }
        if (contentType == null || !storageProperties.allowedContentTypes().contains(contentType)) {
            throw new InvalidDocumentException(
                    "지원하지 않는 형식입니다: %s (허용: %s)"
                            .formatted(contentType, storageProperties.allowedContentTypes()));
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 반드시 지원한다
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }
}
