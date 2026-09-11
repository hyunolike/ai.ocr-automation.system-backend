package com.ocr.automation.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.automation.backend.document.SampleFiles;
import com.ocr.automation.backend.security.apikey.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증. 세 갈래가 각각 다른 수단으로 막혀 있어야 한다.
 *
 * <p>여기서 확인하는 것은 <b>막히는가</b>이고, 소유자 범위가 좁혀지는지는
 * {@code DocumentOwnerIsolationTest} 가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticatedRequests auth;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "scan.png", MediaType.IMAGE_PNG_VALUE,
                SampleFiles.png("내용"));
    }

    // ---------------- 공개 API ----------------

    @Test
    void 키_없이는_공개_API_를_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 업로드도_키가_없으면_거절한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 없는_키는_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/documents").header("X-API-Key", "ocrk_존재하지않는키"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_키는_통과한다() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .header("X-API-Key", auth.issueKeyFor("someone")))
                .andExpect(status().isOk());
    }

    @Test
    void Bearer_형식도_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .header("Authorization", "Bearer " + auth.issueKeyFor("someone")))
                .andExpect(status().isOk());
    }

    @Test
    void 폐기된_키는_거절한다() throws Exception {
        var issued = apiKeyService.issue("someone", "폐기 대상");
        mockMvc.perform(get("/api/v1/documents").header("X-API-Key", issued.plaintext()))
                .andExpect(status().isOk());

        apiKeyService.revoke(issued.prefix());

        mockMvc.perform(get("/api/v1/documents").header("X-API-Key", issued.plaintext()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- 내부 API ----------------

    @Test
    void 토큰_없이는_내부_API_를_쓸_수_없다() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/process-pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 틀린_토큰은_거절한다() throws Exception {
        mockMvc.perform(post("/internal/v1/ocr/process-pending")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void API_키로는_내부_API_를_쓸_수_없다() throws Exception {
        // 외부 클라이언트가 배치를 마음대로 돌릴 수 있으면 안 된다
        mockMvc.perform(post("/internal/v1/ocr/process-pending")
                        .header("X-API-Key", auth.issueKeyFor("someone")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 올바른_토큰은_통과한다() throws Exception {
        mockMvc.perform(auth.asInternal(post("/internal/v1/ocr/process-pending")))
                .andExpect(status().isAccepted());
    }

    // ---------------- 키 발급 ----------------

    @Test
    void 키_발급은_내부_경로에서만_가능하다() throws Exception {
        mockMvc.perform(post("/internal/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"ownerId":"newcomer","label":"연동용"}
                                 """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 발급된_키로_바로_호출할_수_있다() throws Exception {
        MvcResult result = mockMvc.perform(auth.asInternal(post("/internal/v1/api-keys"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"ownerId":"newcomer","label":"연동용"}
                                 """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").isNotEmpty())
                .andExpect(jsonPath("$.prefix").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value("newcomer"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String issuedKey = body.get("key").asText();
        assertThat(issuedKey).startsWith("ocrk_");

        mockMvc.perform(get("/api/v1/documents").header("X-API-Key", issuedKey))
                .andExpect(status().isOk());
    }

    @Test
    void 키_목록에는_평문이_들어_있지_않다() throws Exception {
        var issued = apiKeyService.issue("listed", "목록 확인용");

        MvcResult result = mockMvc.perform(auth.asInternal(get("/internal/v1/api-keys"))
                        .param("ownerId", "listed"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // 서버는 해시만 갖고 있으므로 평문을 다시 보여줄 방법이 애초에 없다
        assertThat(body).doesNotContain(issued.plaintext());
        assertThat(body).contains(issued.prefix());
    }

    // ---------------- 그 밖의 경로 ----------------

    @Test
    void 규칙에_없는_경로는_열리지_않는다() throws Exception {
        // 새 컨트롤러를 추가하고 보안 규칙을 빠뜨리면 열리는 것이 아니라 막혀야 한다
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized());
    }
}
