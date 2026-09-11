package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.domain.OcrResult;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.ocr.OcrDocumentSource;
import com.ocr.automation.backend.ocr.OcrEngine;
import com.ocr.automation.backend.ocr.OcrExtraction;
import com.ocr.automation.backend.storage.DocumentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * OCR 처리 파이프라인의 오케스트레이터.
 *
 * <p>이 클래스 자체에는 {@code @Transactional} 이 없다. 흐름은
 * <b>선점(짧은 트랜잭션) → OCR 실행(트랜잭션 밖) → 완료/실패 기록(짧은 트랜잭션)</b>
 * 이며, 느린 OCR 작업이 DB 커넥션을 붙잡지 않게 하기 위한 의도적인 구조다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentTransitionService transitionService;
    private final DocumentStorage documentStorage;
    private final OcrEngine ocrEngine;

    /**
     * 대기 중인 문서를 최대 {@code batchSize} 건 처리한다.
     * 스케줄러가 주기적으로 호출하는 진입점이다.
     */
    public ProcessingSummary processPending(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 는 1 이상이어야 합니다: " + batchSize);
        }
        List<Long> targets = pendingDocumentIds(batchSize);
        if (targets.isEmpty()) {
            return ProcessingSummary.empty();
        }

        int completed = 0;
        int failed = 0;
        int skipped = 0;
        for (Long documentId : targets) {
            Optional<ClaimedDocument> claimed = transitionService.claim(documentId);
            if (claimed.isEmpty()) {
                skipped++;
                continue;
            }
            if (runOcr(claimed.get())) {
                completed++;
            } else {
                failed++;
            }
        }
        ProcessingSummary summary = new ProcessingSummary(targets.size(), completed, failed, skipped);
        log.info("OCR 배치 처리 완료: {}", summary);
        return summary;
    }

    /**
     * 문서 한 건을 즉시 처리한다. 실패한 문서를 수동으로 다시 돌릴 때 쓴다.
     *
     * @throws InvalidDocumentException 처리 대기 상태가 아닐 때
     */
    public boolean processOne(String publicId) {
        Document document = documentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new DocumentNotFoundException(publicId));
        ClaimedDocument claimed = transitionService.claim(document.getId())
                .orElseThrow(() -> new InvalidDocumentException(
                        "처리 대기 상태가 아닙니다: publicId=%s, status=%s"
                                .formatted(publicId, document.getStatus())));
        return runOcr(claimed);
    }

    /** 중단된 채 PROCESSING 에 멈춰 있는 문서를 회수한다. */
    public int recoverStalled() {
        return transitionService.recoverStalled();
    }

    @Transactional(readOnly = true)
    public List<Long> pendingDocumentIds(int batchSize) {
        return documentRepository
                .findByStatusOrderByUploadedAtAsc(DocumentStatus.PENDING, PageRequest.of(0, batchSize))
                .stream()
                .map(Document::getId)
                .toList();
    }

    /**
     * 실제 OCR 수행. 트랜잭션 밖에서 돈다.
     *
     * @return 성공했으면 true
     */
    private boolean runOcr(ClaimedDocument claimed) {
        long startedAt = System.currentTimeMillis();
        try {
            byte[] content = documentStorage.read(claimed.storageKey());
            OcrExtraction extraction = ocrEngine.extract(new OcrDocumentSource(
                    claimed.originalFilename(), claimed.contentType(), content));

            OcrResult result = OcrResult.of(
                    extraction.text(),
                    extraction.confidence(),
                    ocrEngine.name(),
                    extraction.language(),
                    extraction.pageCount(),
                    System.currentTimeMillis() - startedAt);
            transitionService.complete(claimed.id(), result);
            return true;
        } catch (Exception e) {
            // 어떤 이유로 실패했든 문서는 PENDING(재시도) 또는 FAILED 로 반드시 정리한다.
            // 여기서 예외가 새어나가면 문서가 PROCESSING 에 영영 남는다.
            log.error("OCR 처리 중 오류: publicId={}", claimed.publicId(), e);
            safelyRecordFailure(claimed, e);
            return false;
        }
    }

    private void safelyRecordFailure(ClaimedDocument claimed, Exception cause) {
        try {
            transitionService.fail(claimed.id(), describe(cause));
        } catch (Exception e) {
            // 실패 기록마저 실패하면 문서는 PROCESSING 에 남는다.
            // recoverStalled() 가 나중에 회수하므로 여기서는 로그만 남긴다.
            log.error("실패 상태 기록에 실패했습니다. 정체 문서 회수에 맡깁니다: publicId={}",
                    claimed.publicId(), e);
        }
    }

    private String describe(Exception e) {
        return "%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage());
    }
}
