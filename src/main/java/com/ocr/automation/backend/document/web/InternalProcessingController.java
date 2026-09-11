package com.ocr.automation.backend.document.web;

import com.ocr.automation.backend.document.service.DocumentProcessingService;
import com.ocr.automation.backend.document.web.dto.DispatchResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 스케줄러(ocr-scheduler)가 호출하는 내부 API.
 *
 * <p><b>외부에 노출하면 안 된다.</b> 지금은 경로만 `/internal` 로 갈라두었고
 * 인증은 없다. 실제 운영에서는 내부망 제한이나 서비스 간 인증이 필요하다.
 *
 * <p>OCR 실행 자체는 backend 에 둔다. 엔진 의존성(tesseract 네이티브)과
 * 스토리지 접근을 한 서비스에만 모아두기 위해서다. 스케줄러는 "언제 돌릴지"만 안다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/ocr")
@RequiredArgsConstructor
public class InternalProcessingController {

    private static final int MAX_BATCH_SIZE = 200;

    private final DocumentProcessingService documentProcessingService;

    /**
     * 대기 중인 문서를 워커 풀에 <b>접수</b>한다. 처리를 기다리지 않고 즉시 반환한다.
     *
     * <p>그래서 202 다. 응답의 queued 는 큐에 넣은 수일 뿐 성공한 수가 아니다.
     */
    @PostMapping("/process-pending")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DispatchResultResponse processPending(
            @RequestParam(defaultValue = "20") int batchSize) {
        return DispatchResultResponse.from(
                documentProcessingService.processPending(Math.clamp(batchSize, 1, MAX_BATCH_SIZE)));
    }

    /** 문서 한 건을 동기로 처리하고 결과를 돌려준다 (수동 재처리용). */
    @PostMapping("/documents/{publicId}/process")
    public Map<String, Object> processOne(@PathVariable String publicId) {
        boolean succeeded = documentProcessingService.processOne(publicId);
        return Map.of("id", publicId, "succeeded", succeeded);
    }

    /** 중단된 채 PROCESSING 에 멈춘 문서를 회수한다. */
    @PostMapping("/recover-stalled")
    public Map<String, Object> recoverStalled() {
        int recovered = documentProcessingService.recoverStalled();
        return Map.of("recovered", recovered);
    }
}
