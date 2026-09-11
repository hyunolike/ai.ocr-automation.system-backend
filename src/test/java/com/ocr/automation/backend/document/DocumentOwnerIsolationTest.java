package com.ocr.automation.backend.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.automation.backend.document.repository.DocumentRepository;
import com.ocr.automation.backend.security.AuthenticatedRequests;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소유자 격리. 공개 API 의 <b>모든</b> 경로에서 남의 문서가 새어나가지 않아야 한다.
 *
 * <p>이 테스트가 인증을 검증하는 것은 아니다. 지금 소유자는 헤더에서 오고 헤더는
 * 누구나 보낼 수 있다(Phase 1.3 에서 교체). 여기서 확인하는 것은
 * <b>소유자가 주어졌을 때 조회 범위가 그 소유자로 좁혀지는가</b>이다.
 * 인가할 대상을 먼저 만들어두지 않으면 인증을 붙여도 막을 것이 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentOwnerIsolationTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String API_KEY = "X-API-Key";

    @Autowired
    private AuthenticatedRequests auth;

    /** 소유자마다 실제 키를 발급받는다. 이제 소유자는 주장이 아니라 증명이다. */
    private String aliceKey;
    private String bobKey;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        aliceKey = auth.issueKeyFor(ALICE);
        bobKey = auth.issueKeyFor(BOB);
    }

    private String keyOf(String ownerId) {
        return ALICE.equals(ownerId) ? aliceKey : bobKey;
    }

    private MockMultipartFile pngFile(String filename, String body) {
        return new MockMultipartFile(
                "file", filename, MediaType.IMAGE_PNG_VALUE, body.getBytes(StandardCharsets.UTF_8));
    }

    private String uploadAs(String ownerId, String filename, String body) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(pngFile(filename, body))
                        .header(API_KEY, keyOf(ownerId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    @Test
    void 남의_문서는_조회되지_않는다() throws Exception {
        String aliceDocument = uploadAs(ALICE, "alice.png", "앨리스의 문서");

        mockMvc.perform(get("/api/v1/documents/{id}", aliceDocument).header(API_KEY, bobKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void 남의_문서_텍스트도_조회되지_않는다() throws Exception {
        String aliceDocument = uploadAs(ALICE, "alice.png", "앨리스의 문서");

        mockMvc.perform(get("/api/v1/documents/{id}/text", aliceDocument).header(API_KEY, bobKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void 남의_문서에는_403_이_아니라_404_를_준다() throws Exception {
        String aliceDocument = uploadAs(ALICE, "alice.png", "앨리스의 문서");

        // 403 은 "그 문서가 존재한다"는 사실을 알려주는 셈이라 그 자체로 정보가 샌다.
        // 없는 문서를 조회했을 때와 응답이 구분되지 않아야 한다.
        String forOther = mockMvc.perform(get("/api/v1/documents/{id}", aliceDocument).header(API_KEY, bobKey))
                .andReturn().getResponse().getContentAsString();
        String forMissing = mockMvc.perform(get("/api/v1/documents/{id}", "없는-문서").header(API_KEY, bobKey))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(forOther).get("code").asText())
                .isEqualTo(objectMapper.readTree(forMissing).get("code").asText());
    }

    @Test
    void 목록은_자기_문서만_보여준다() throws Exception {
        uploadAs(ALICE, "a1.png", "앨리스 1");
        uploadAs(ALICE, "a2.png", "앨리스 2");
        uploadAs(BOB, "b1.png", "밥 1");

        mockMvc.perform(get("/api/v1/documents").header(API_KEY, aliceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/documents").header(API_KEY, bobKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        assertThat(documentRepository.count()).isEqualTo(3);
    }

    @Test
    void 같은_파일을_다른_소유자가_올리면_각각_생성된다() throws Exception {
        String aliceDocument = uploadAs(ALICE, "same.png", "똑같은 내용");
        String bobDocument = uploadAs(BOB, "same.png", "똑같은 내용");

        // 중복 판정이 전역이면 밥이 앨리스의 문서 ID 를 돌려받는다. 정보 노출이다.
        assertThat(bobDocument).isNotEqualTo(aliceDocument);
        assertThat(documentRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_소유자가_같은_파일을_다시_올리면_재사용한다() throws Exception {
        String first = uploadAs(ALICE, "same.png", "똑같은 내용");
        String second = uploadAs(ALICE, "다른이름.png", "똑같은 내용");

        assertThat(second).isEqualTo(first);
        assertThat(documentRepository.count()).isEqualTo(1);
    }

    @Test
    void 소유자는_이제_주장이_아니라_증명이다() throws Exception {
        String aliceDocument = uploadAs(ALICE, "alice.png", "앨리스의 문서");

        // 밥이 자기 키로 앨리스를 "주장"할 방법이 없다. 소유자는 키에 묶여 있고
        // 요청 어디에도 소유자를 지정하는 자리가 없다.
        mockMvc.perform(get("/api/v1/documents/{id}", aliceDocument)
                        .header(API_KEY, bobKey)
                        .header("X-Owner-Id", ALICE))   // 옛 헤더는 이제 아무 의미가 없다
                .andExpect(status().isNotFound());
    }

    @Test
    void 내부_처리_API_는_소유자를_가리지_않는다() throws Exception {
        uploadAs(ALICE, "a.png", "앨리스 문서");
        uploadAs(BOB, "b.png", "밥 문서");

        // 스케줄러는 시스템 전체의 대기 문서를 집어야 한다. 소유자로 나뉘면 안 된다.
        mockMvc.perform(auth.asInternal(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/internal/v1/ocr/process-pending")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queued").value(2));
    }
}
