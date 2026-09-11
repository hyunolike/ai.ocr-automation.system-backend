package com.ocr.automation.backend.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
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

    private MockMultipartFile pngFile(String filename, String body) {
        return new MockMultipartFile(
                "file", filename, MediaType.IMAGE_PNG_VALUE, body.getBytes(StandardCharsets.UTF_8));
    }

    private String uploadAndGetId(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    @Test
    void 업로드하면_대기_상태로_등록된다() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents").file(pngFile("scan.png", "업로드-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.originalFilename").value("scan.png"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ocrResult").doesNotExist());
    }

    @Test
    void 업로드부터_처리_조회까지_이어진다() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "파이프라인-전체"));

        mockMvc.perform(post("/internal/v1/ocr/process-pending").param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picked").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        mockMvc.perform(get("/api/v1/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ocrResult.engine").value("stub"))
                .andExpect(jsonPath("$.ocrResult.textLength").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.ocrResult.processedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/documents/{id}/text", id))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("stub-ocr")
                        .contains("scan.png"));
    }

    @Test
    void 처리_전에_텍스트를_요청하면_400() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "아직-처리-안됨"));

        mockMvc.perform(get("/api/v1/documents/{id}/text", id))
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

        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 빈_파일은_거부한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void 없는_문서를_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/v1/documents/{id}", "존재하지-않는-아이디"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void 상태로_목록을_거를_수_있다() throws Exception {
        uploadAndGetId(pngFile("a.png", "목록-A"));
        uploadAndGetId(pngFile("b.png", "목록-B"));

        mockMvc.perform(get("/api/v1/documents").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/documents").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 이미_처리된_문서를_다시_처리하면_409() throws Exception {
        String id = uploadAndGetId(pngFile("scan.png", "재처리-대상"));
        mockMvc.perform(post("/internal/v1/ocr/process-pending")).andExpect(status().isOk());

        mockMvc.perform(post("/internal/v1/ocr/documents/{id}/process", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));

        assertThat(documentRepository.findByPublicId(id))
                .get()
                .satisfies(document -> assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED));
    }

    @Test
    void 대기_문서가_없으면_아무것도_처리하지_않는다() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/process-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picked").value(0))
                .andExpect(jsonPath("$.completed").value(0));
    }

    @Test
    void 정체된_문서가_없으면_회수_건수는_0() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/recover-stalled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(0));
    }
}
