package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.domain.OcrResult;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 문서 상태 전이만 담당하는 계층.
 *
 * <p>OCR 처리는 수 초에서 수십 초가 걸린다. 그동안 DB 커넥션을 잡고 있으면
 * 커넥션 풀이 금방 마르므로 <b>상태 전이(짧은 트랜잭션)와 OCR 실행(트랜잭션 밖)을
 * 분리</b>했다. {@link DocumentProcessingService} 가 이 클래스를 호출해
 * 선점 → (트랜잭션 밖 OCR) → 완료/실패 순서로 진행한다.
 *
 * <p>같은 클래스 안에서 호출하면 프록시를 타지 않아 트랜잭션이 분리되지 않으므로
 * 별도 빈으로 둔 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTransitionService {

    private final DocumentRepository documentRepository;
    private final ProcessingProperties processingProperties;

    /**
     * 문서를 PROCESSING 으로 선점한다.
     *
     * <p>스케줄러가 여러 인스턴스로 떠 있으면 같은 문서를 동시에 집을 수 있다.
     * 그 경우 {@code @Version} 낙관적 락이 충돌을 잡아내고, 진 쪽은
     * {@link Optional#empty()} 를 받아 조용히 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedDocument> claim(Long documentId) {
        try {
            return documentRepository.findById(documentId)
                    .filter(document -> document.getStatus() == DocumentStatus.PENDING)
                    .map(document -> {
                        document.startProcessing();
                        documentRepository.saveAndFlush(document);
                        return ClaimedDocument.from(document);
                    });
        } catch (OptimisticLockingFailureException e) {
            log.debug("다른 인스턴스가 먼저 선점했습니다: documentId={}", documentId);
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long documentId, OcrResult result) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("문서가 사라졌습니다: id=" + documentId));
        document.completeWith(result);
        log.info("OCR 완료: publicId={}, engine={}, textLength={}",
                document.getPublicId(), result.getEngine(), result.textLength());
    }

    /**
     * 처리 실패를 기록한다.
     *
     * @return 재시도 대기(PENDING)로 돌아갔으면 true, FAILED 로 확정됐으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Long documentId, String reason) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("문서가 사라졌습니다: id=" + documentId));
        boolean willRetry = document.fail(reason, processingProperties.maxRetries());
        log.warn("OCR 실패: publicId={}, retryCount={}, willRetry={}, reason={}",
                document.getPublicId(), document.getRetryCount(), willRetry, reason);
        return willRetry;
    }

    /**
     * 처리 도중 인스턴스가 죽어 PROCESSING 에 멈춰 있는 문서를 회수한다.
     *
     * @return 회수한 문서 수
     */
    @Transactional
    public int recoverStalled() {
        Instant threshold = Instant.now().minus(processingProperties.staleAfter());
        List<Document> stalled = documentRepository
                .findByStatusAndProcessingStartedAtBefore(DocumentStatus.PROCESSING, threshold);
        stalled.forEach(document -> {
            boolean willRetry = document.recoverFromStall(processingProperties.maxRetries());
            log.warn("정체된 문서 회수: publicId={}, willRetry={}", document.getPublicId(), willRetry);
        });
        return stalled.size();
    }
}
