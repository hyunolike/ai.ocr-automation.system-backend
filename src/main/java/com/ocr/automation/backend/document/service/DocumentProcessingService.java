package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.config.OcrExecutorConfig;
import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.domain.OcrResult;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.ocr.OcrDocumentSource;
import com.ocr.automation.backend.ocr.OcrEngine;
import com.ocr.automation.backend.ocr.OcrExtraction;
import com.ocr.automation.backend.storage.DocumentStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * OCR 처리 파이프라인의 오케스트레이터.
 *
 * <p>이 클래스에는 {@code @Transactional} 이 없다. 흐름은
 * <b>선점(짧은 트랜잭션) → 워커 풀에 위임 → OCR(트랜잭션 밖) → 완료/실패 기록(짧은 트랜잭션)</b>
 * 이며, 느린 OCR 작업이 DB 커넥션도 HTTP 요청 스레드도 붙잡지 않게 하기 위한 구조다.
 *
 * <p>{@link #processPending} 은 <b>접수만 하고 즉시 반환한다.</b> 동기로 끝까지 처리하면
 * 배치 크기 × 건당 소요가 호출자의 읽기 타임아웃을 넘기고, 요청이 끊긴 뒤에도 처리는
 * 계속 돌아 다음 주기 요청과 겹친다.
 */
@Slf4j
@Service
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentTransitionService transitionService;
    private final DocumentStorage documentStorage;
    private final OcrEngine ocrEngine;
    private final Executor ocrExecutor;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     DocumentTransitionService transitionService,
                                     DocumentStorage documentStorage,
                                     OcrEngine ocrEngine,
                                     @Qualifier(OcrExecutorConfig.OCR_EXECUTOR) Executor ocrExecutor) {
        this.documentRepository = documentRepository;
        this.transitionService = transitionService;
        this.documentStorage = documentStorage;
        this.ocrEngine = ocrEngine;
        this.ocrExecutor = ocrExecutor;
    }

    /**
     * 대기 중인 문서를 최대 {@code batchSize} 건 <b>접수</b>한다.
     * 스케줄러가 주기적으로 호출하는 진입점이다.
     */
    public DispatchResult processPending(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 는 1 이상이어야 합니다: " + batchSize);
        }
        List<Long> targets = pendingDocumentIds(batchSize);
        if (targets.isEmpty()) {
            return DispatchResult.empty();
        }

        int queued = 0;
        int skipped = 0;
        for (int i = 0; i < targets.size(); i++) {
            Long documentId = targets.get(i);

            Optional<ClaimedDocument> claimed = transitionService.claim(documentId);
            if (claimed.isEmpty()) {
                skipped++;
                continue;
            }

            if (!submit(claimed.get())) {
                // 큐가 가득 찼다. 이 뒤의 문서도 마찬가지이므로 더 선점하지 않고 멈춘다.
                // 남은 문서는 그대로 PENDING 이라 다음 주기에 다시 집힌다.
                int rejected = targets.size() - i;
                DispatchResult result = new DispatchResult(queued, rejected, skipped);
                log.warn("OCR 워커 큐가 가득 찼습니다. 다음 주기에 다시 시도합니다: {}", result);
                return result;
            }
            queued++;
        }

        DispatchResult result = new DispatchResult(queued, 0, skipped);
        log.info("OCR 접수 완료: {}", result);
        return result;
    }

    /**
     * 문서 한 건을 <b>동기로</b> 처리한다. 실패한 문서를 수동으로 다시 돌릴 때 쓴다.
     *
     * <p>배치와 달리 동기로 두는 이유: 호출자가 한 건을 지목해 요청했으므로 결과를
     * 바로 돌려주는 편이 쓸모 있고, 한 건이라 소요가 배치처럼 누적되지 않는다.
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

    /**
     * 워커 풀에 작업을 넘긴다.
     *
     * @return 받아들여졌으면 true, 큐가 가득 차 거부됐으면 false
     */
    private boolean submit(ClaimedDocument claimed) {
        try {
            ocrExecutor.execute(() -> runOcr(claimed));
            return true;
        } catch (RejectedExecutionException e) {
            // 선점해두고 처리하지 못하게 됐으므로 되돌린다.
            // 재시도 횟수는 올리지 않는다 — 큐가 붐빈 것은 문서의 잘못이 아니다.
            releaseQuietly(claimed);
            return false;
        }
    }

    private void releaseQuietly(ClaimedDocument claimed) {
        try {
            transitionService.releaseClaim(claimed.id());
        } catch (Exception e) {
            // 되돌리지 못하면 문서가 PROCESSING 에 남는다. 정체 회수 잡이 걷어간다.
            log.error("선점 해제에 실패했습니다. 정체 문서 회수에 맡깁니다: publicId={}",
                    claimed.publicId(), e);
        }
    }

    /**
     * 대기 중인 문서 id 를 오래 기다린 순서로 가져온다.
     *
     * <p>트랜잭션을 걸지 않는다. 리포지토리 호출 하나뿐이라 Spring Data 가 여는
     * 자체 트랜잭션으로 충분하다. (같은 클래스에서 호출하면 프록시를 타지 않아
     * {@code @Transactional} 이 애초에 적용되지도 않는다.)
     */
    private List<Long> pendingDocumentIds(int batchSize) {
        return documentRepository
                .findByStatusOrderByUploadedAtAsc(DocumentStatus.PENDING, PageRequest.of(0, batchSize))
                .stream()
                .map(Document::getId)
                .toList();
    }

    /**
     * 실제 OCR 수행. 워커 스레드에서, 트랜잭션 밖에서 돈다.
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
