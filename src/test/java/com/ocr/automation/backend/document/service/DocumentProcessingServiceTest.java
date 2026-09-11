package com.ocr.automation.backend.document.service;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.ocr.OcrEngine;
import com.ocr.automation.backend.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 접수 단계의 규칙을 검증한다.
 *
 * <p>특히 <b>큐가 가득 찼을 때</b>가 중요하다. 선점만 해두고 작업을 넘기지 못하면
 * 문서가 PROCESSING 에 갇힌다. 그 경우 선점을 되돌려야 하고, 재시도 횟수를
 * 올려서는 안 된다 — 큐가 붐빈 것은 문서의 잘못이 아니기 때문이다.
 */
class DocumentProcessingServiceTest {

    private DocumentRepository documentRepository;
    private DocumentTransitionService transitionService;
    private Executor ocrExecutor;
    private DocumentProcessingService service;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(DocumentRepository.class);
        transitionService = Mockito.mock(DocumentTransitionService.class);
        ocrExecutor = Mockito.mock(Executor.class);
        service = new DocumentProcessingService(
                documentRepository,
                transitionService,
                Mockito.mock(DocumentStorage.class),
                Mockito.mock(OcrEngine.class),
                ocrExecutor);
    }

    private Document documentWithId(long id) {
        Document document = Document.register(
                "scan-%d.png".formatted(id), "image/png", 100L, "checksum-" + id, "key-" + id);
        // id 는 JPA 가 채우는 값이라 테스트에서는 리플렉션으로 넣는다
        try {
            var field = Document.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(document, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return document;
    }

    private void givenPending(long... ids) {
        List<Document> documents = java.util.Arrays.stream(ids).mapToObj(this::documentWithId).toList();
        when(documentRepository.findByStatusOrderByUploadedAtAsc(eq(DocumentStatus.PENDING), any(Pageable.class)))
                .thenReturn(documents);
        for (long id : ids) {
            when(transitionService.claim(id)).thenReturn(Optional.of(
                    new ClaimedDocument(id, "public-" + id, "scan.png", "image/png", "key-" + id)));
        }
    }

    @Test
    void 대기_문서가_없으면_아무것도_접수하지_않는다() {
        when(documentRepository.findByStatusOrderByUploadedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        DispatchResult result = service.processPending(10);

        assertThat(result.queued()).isZero();
        assertThat(result.didNothing()).isTrue();
        verify(ocrExecutor, never()).execute(any());
    }

    @Test
    void 선점한_문서를_워커_풀에_넘긴다() {
        givenPending(1L, 2L, 3L);

        DispatchResult result = service.processPending(10);

        assertThat(result.queued()).isEqualTo(3);
        assertThat(result.rejected()).isZero();
        assertThat(result.skipped()).isZero();
        verify(ocrExecutor, Mockito.times(3)).execute(any());
    }

    @Test
    void 다른_인스턴스가_먼저_선점한_문서는_건너뛴다() {
        givenPending(1L, 2L);
        when(transitionService.claim(1L)).thenReturn(Optional.empty());

        DispatchResult result = service.processPending(10);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.queued()).isEqualTo(1);
        verify(ocrExecutor, Mockito.times(1)).execute(any());
    }

    @Test
    void 큐가_가득_차면_선점을_되돌린다() {
        givenPending(1L);
        Mockito.doThrow(new RejectedExecutionException("queue full")).when(ocrExecutor).execute(any());

        DispatchResult result = service.processPending(10);

        assertThat(result.queued()).isZero();
        assertThat(result.rejected()).isEqualTo(1);
        // 선점을 되돌리되, 실패로 세지 않는다
        verify(transitionService).releaseClaim(1L);
        verify(transitionService, never()).fail(anyLong(), any());
    }

    @Test
    void 큐가_가득_차면_남은_문서는_선점하지_않는다() {
        givenPending(1L, 2L, 3L);
        Mockito.doThrow(new RejectedExecutionException("queue full")).when(ocrExecutor).execute(any());

        DispatchResult result = service.processPending(10);

        assertThat(result.rejected()).isEqualTo(3);   // 첫 건 + 시도조차 않은 2건
        verify(transitionService).claim(1L);
        verify(transitionService, never()).claim(2L); // 더 선점하지 않는다
        verify(transitionService, never()).claim(3L);
    }

    @Test
    void 선점_해제가_실패해도_접수는_계속_끝난다() {
        givenPending(1L);
        Mockito.doThrow(new RejectedExecutionException("queue full")).when(ocrExecutor).execute(any());
        Mockito.doThrow(new IllegalStateException("문서가 사라졌습니다"))
                .when(transitionService).releaseClaim(1L);

        // 정체 회수 잡이 걷어가므로 여기서 터뜨리지 않는다
        DispatchResult result = service.processPending(10);

        assertThat(result.rejected()).isEqualTo(1);
    }

    @Test
    void 배치_크기가_0_이하면_거부한다() {
        assertThatThrownBy(() -> service.processPending(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }
}
