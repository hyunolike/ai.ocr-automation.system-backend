package com.ocr.automation.backend.document.repository;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByPublicId(String publicId);

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);

    /** 오래 기다린 문서부터 처리한다. */
    List<Document> findByStatusOrderByUploadedAtAsc(DocumentStatus status, Pageable pageable);

    /** 처리에 착수한 지 오래된 PROCESSING 문서 (중단된 것으로 본다). */
    List<Document> findByStatusAndProcessingStartedAtBefore(DocumentStatus status, Instant threshold);

    /** 같은 내용의 문서가 이미 올라와 있는지 확인한다. */
    Optional<Document> findFirstByChecksumOrderByUploadedAtDesc(String checksum);

    long countByStatus(DocumentStatus status);
}
