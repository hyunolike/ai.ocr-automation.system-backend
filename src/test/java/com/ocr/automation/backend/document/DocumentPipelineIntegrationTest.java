package com.ocr.automation.backend.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import com.ocr.automation.backend.owner.header.HeaderDocumentOwnerResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 → 스케줄 처리 → 조회까지 파이프라인 전체를 HTTP 로 검증한다.
 *
 * <p>OCR 엔진은 stub 이다(네이티브 tesseract 없이 돌아야 하므로).
 * 엔진을 바꿔도 이 흐름은 그대로여야 한다는 것이 이 테스트의 요지다.
 *
 * <p>처리는 <b>비동기</b>다. {@code /process-pending} 은 202 로 접수만 하므로
 * 응답이 왔다고 처리가 끝난 것이 아니다. 결과는 문서 상태를 폴링해 확인한다.
 *
 * <p>스키마는 Flyway 가 만들고 JPA 는 validate 만 한다. 엔티티와 마이그레이션이
 * 어긋나면 이 테스트가 기동 단계에서 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDocuments() {
        documentRepository.deleteAll();
    }

    private static final String OWNER = "owner-a";
    private static final String OTHER_OWNER = "owner-b";

    private MockMultipartFile pngFile(String filename, String body) {
        return new MockMultipartFile(
                "file", filename, MediaType.IMAGE_PNG_VALUE, body.getBytes(StandardCharsets.UTF_8));
    }

    /** 문서가 해당 상태에 이를 때까지 기다린다. 처리는 워커 스레드에서 비동기로 돈다. */
    private void awaitStatus(String publicId, DocumentStatus expected) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(documentRepository.findByPublicId(publicId))
                        .get()
                        .satisfies(document -> assertThat(document.getStatus()).isEqualTo(expected)));
    }

    private String uploadAndGetId(MockMultipartFile file) throws Exception {
        return uploadAndGetId(OWNER, file);
    }

    private String uploadAndGetId(String ownerId, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, ownerId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    @Test
    void 업로드하면_대기_상태로_등록된다() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents").file(pngFile("scan.png", "업로드-1"))
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.originalFilename").value("scan.png"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ocrResult").doesNotExist());
    }

    @Test
    void 업로드부터_처리_조회까지_이어진다() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "파이프라인-전체"));

        // 접수만 하고 즉시 202 를 돌려준다. 이 시점에 처리는 끝나지 않았다.
        mockMvc.perform(post("/internal/v1/ocr/process-pending").param("batchSize", "10"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.skipped").value(0));

        awaitStatus(id, DocumentStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ocrResult.engine").value("stub"))
                .andExpect(jsonPath("$.ocrResult.textLength").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.ocrResult.processedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/documents/{id}/text", id).header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("stub-ocr")
                        .contains("scan.png"));
    }

    @Test
    void 처리_전에_텍스트를_요청하면_400() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "아직-처리-안됨"));

        mockMvc.perform(get("/api/v1/documents/{id}/text", id).header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 같은_내용을_다시_올리면_기존_문서를_돌려준다() throws Exception {
        MockMultipartFile file = pngFile("scan.png", "중복-내용");

        String first = uploadAndGetId(file);
        String second = uploadAndGetId(pngFile("다른이름.png", "중복-내용"));

        assertThat(second).isEqualTo(first);
        assertThat(documentRepository.count()).isEqualTo(1);
    }

    @Test
    void 지원하지_않는_형식은_거부한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", MediaType.TEXT_PLAIN_VALUE, "본문".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 빈_파일은_거부한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 없는_문서를_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/v1/documents/{id}", "존재하지-않는-아이디").header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void 상태로_목록을_거를_수_있다() throws Exception {
        uploadAndGetId(pngFile("a.png", "목록-A"));
        uploadAndGetId(pngFile("b.png", "목록-B"));

        mockMvc.perform(get("/api/v1/documents")
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/documents")
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER)
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 이미_처리된_문서는_다시_처리할_수_없다() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "재처리-대상"));
        mockMvc.perform(post("/internal/v1/ocr/process-pending")).andExpect(status().isAccepted());
        awaitStatus(id, DocumentStatus.COMPLETED);

        mockMvc.perform(post("/internal/v1/ocr/documents/{id}/process", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 대기_문서가_없으면_아무것도_접수하지_않는다() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/process-pending"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queued").value(0))
                .andExpect(jsonPath("$.rejected").value(0));
    }

    @Test
    void 실패한_문서를_다시_올리면_재처리_대기로_돌아간다() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "실패-후-재업로드"));

        // 재시도 여유를 모두 소진시켜 FAILED 로 만든다
        Document document = documentRepository.findByPublicId(id).orElseThrow();
        for (int attempt = 0; attempt < 3; attempt++) {
            document.startProcessing();
            document.fail("테스트용 강제 실패", 3);
        }
        documentRepository.saveAndFlush(document);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);

        // 같은 내용을 다시 업로드
        mockMvc.perform(multipart("/api/v1/documents").file(pngFile("scan.png", "실패-후-재업로드"))
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))          // 같은 문서를 재사용하고
                .andExpect(jsonPath("$.status").value("PENDING"))  // 다시 대기열에 오른다
                .andExpect(jsonPath("$.retryCount").value(0));     // 재시도 횟수도 초기화된다

        // 실제로 다시 처리된다
        mockMvc.perform(post("/internal/v1/ocr/process-pending"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queued").value(1));
        awaitStatus(id, DocumentStatus.COMPLETED);
    }

    @Test
    void 완료된_문서를_다시_올리면_그대로_돌려준다() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "완료-후-재업로드"));
        mockMvc.perform(post("/internal/v1/ocr/process-pending")).andExpect(status().isAccepted());
        awaitStatus(id, DocumentStatus.COMPLETED);

        mockMvc.perform(multipart("/api/v1/documents").file(pngFile("scan.png", "완료-후-재업로드"))
                        .header(HeaderDocumentOwnerResolver.OWNER_HEADER, OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(documentRepository.count()).isEqualTo(1);
    }

    @Test
    void 여러_문서를_한번에_접수하면_모두_처리된다() throws Exception {
        String first = uploadAndGetId(pngFile("a.png", "배치-A"));
        String second = uploadAndGetId(pngFile("b.png", "배치-B"));
        String third = uploadAndGetId(pngFile("c.png", "배치-C"));

        mockMvc.perform(post("/internal/v1/ocr/process-pending").param("batchSize", "10"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queued").value(3))
                .andExpect(jsonPath("$.rejected").value(0));

        awaitStatus(first, DocumentStatus.COMPLETED);
        awaitStatus(second, DocumentStatus.COMPLETED);
        awaitStatus(third, DocumentStatus.COMPLETED);
    }

    @Test
    void 정체된_문서가_없으면_회수_건수는_0() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/recover-stalled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(0));
    }
}
