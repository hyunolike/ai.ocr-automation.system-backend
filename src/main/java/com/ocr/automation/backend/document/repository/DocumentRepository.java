package com.ocr.automation.backend.document.repository;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 조회 메서드를 두 갈래로 나눠 둔다.
 *
 * <ul>
 *   <li><b>소유자 범위</b> — 공개 API 가 쓴다. 소유자 조건이 반드시 붙는다.</li>
 *   <li><b>시스템 범위</b> — 스케줄러가 부르는 내부 처리가 쓴다. 소유자를 가리지 않는다.</li>
 * </ul>
 *
 * <p>공개 API 에서 시스템 범위 메서드를 쓰면 남의 문서가 새어나간다.
 * 그래서 이름과 주석으로 갈라두었다.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // ------------------------------------------------------------------
    // 소유자 범위 — 공개 API 전용
    // ------------------------------------------------------------------

    Optional<Document> findByPublicIdAndOwnerId(String publicId, String ownerId);

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndStatus(String ownerId, DocumentStatus status, Pageable pageable);

    /**
     * 같은 내용의 문서가 이 소유자에게 이미 있는지 확인한다.
     *
     * <p>전역으로 보면 안 된다. 다른 사용자가 올린 파일과 같은 파일을 올렸을 때
     * 남의 문서 ID 를 돌려받게 되어 정보가 새어나간다.
     */
    Optional<Document> findFirstByOwnerIdAndChecksumOrderByUploadedAtDesc(String ownerId, String checksum);

    // ------------------------------------------------------------------
    // 시스템 범위 — 내부 처리(스케줄러) 전용. 공개 API 에서 쓰지 말 것
    // ------------------------------------------------------------------

    /** 내부 처리에서 문서를 지목할 때만 쓴다. 공개 조회는 위의 소유자 범위 메서드를 쓴다. */
    Optional<Document> findByPublicId(String publicId);

    /** 오래 기다린 문서부터 처리한다. 시스템 전체의 대기 문서를 본다. */
    List<Document> findByStatusOrderByUploadedAtAsc(DocumentStatus status, Pageable pageable);

    /** 처리에 착수한 지 오래된 PROCESSING 문서 (중단된 것으로 본다). */
    List<Document> findByStatusAndProcessingStartedAtBefore(DocumentStatus status, Instant threshold);

    long countByStatus(DocumentStatus status);
}
