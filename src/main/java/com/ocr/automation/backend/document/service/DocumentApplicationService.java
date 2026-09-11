package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.document.validation.ContentTypeVerifier;
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
    private final ContentTypeVerifier contentTypeVerifier;

    /**
     * 문서를 저장하고 OCR 대기열(PENDING)에 올린다.
     *
     * <p>같은 소유자가 같은 내용(체크섬 일치)의 문서를 이미 올렸으면 새로 저장하지 않고
     * 기존 문서를 돌려준다. 스토리지에 같은 파일이 쌓이는 것을 막는다.
     *
     * <p>중복 판정은 <b>소유자 범위</b>다. 전역으로 보면 다른 사용자가 올린 파일과 같은
     * 파일을 올렸을 때 남의 문서 ID 를 돌려받게 된다.
     *
     * <p>단 기존 문서가 {@code FAILED} 라면 다시 대기열에 올린다. 그러지 않으면
     * 사용자가 실패한 문서를 다시 올려도 아무 일도 일어나지 않는다.
     */
    @Transactional
    public Document upload(String ownerId, String originalFilename, String contentType, byte[] content) {
        validate(originalFilename, contentType, content);

        String checksum = sha256(content);
        Optional<Document> existing =
                documentRepository.findFirstByOwnerIdAndChecksumOrderByUploadedAtDesc(ownerId, checksum);
        if (existing.isPresent()) {
            return reuse(existing.get(), checksum);
        }

        String storageKey = documentStorage.store(originalFilename, content);
        Document document = Document.register(
                ownerId, originalFilename, contentType, content.length, checksum, storageKey);
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

    /**
     * 소유자의 문서를 찾는다.
     *
     * <p>남의 문서를 지목했을 때 403 이 아니라 <b>404 로 응답하도록</b> 같은 예외를 던진다.
     * 403 은 "그 문서가 존재한다"는 사실을 알려주는 셈이라 그 자체로 정보가 샌다.
     */
    @Transactional(readOnly = true)
    public Document findByPublicId(String ownerId, String publicId) {
        return documentRepository.findByPublicIdAndOwnerId(publicId, ownerId)
                .orElseThrow(() -> new DocumentNotFoundException(publicId));
    }

    @Transactional(readOnly = true)
    public Page<Document> findAll(String ownerId, DocumentStatus status, Pageable pageable) {
        return status == null
                ? documentRepository.findByOwnerId(ownerId, pageable)
                : documentRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
    }

    @Transactional(readOnly = true)
    public String extractedTextOf(String ownerId, String publicId) {
        Document document = findByPublicId(ownerId, publicId);
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
        // 헤더가 아니라 내용을 본다. 확장자와 헤더는 얼마든지 바꿀 수 있다.
        contentTypeVerifier.verify(contentType, content);
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
