package com.ocr.automation.backend.document.web;

import com.ocr.automation.backend.document.domain.Document;
import com.ocr.automation.backend.document.domain.DocumentStatus;
import com.ocr.automation.backend.document.service.DocumentApplicationService;
import com.ocr.automation.backend.document.service.InvalidDocumentException;
import com.ocr.automation.backend.document.web.dto.DocumentResponse;
import com.ocr.automation.backend.owner.DocumentOwnerResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

/**
 * 문서 업로드/조회 공개 API.
 *
 * <p>어댑터 계층이다. 비즈니스 로직은 두지 않고 DTO 변환과 위임만 한다.
 *
 * <p>소유자는 <b>요청 파라미터로 받지 않는다.</b> {@link DocumentOwnerResolver} 에 묻는다.
 * 클라이언트가 보낸 값을 그대로 쓰면 파라미터 하나로 남의 문서를 조회할 수 있다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentApplicationService documentApplicationService;
    private final DocumentOwnerResolver ownerResolver;

    /** 문서를 업로드하고 OCR 대기열에 올린다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        Document document = documentApplicationService.upload(
                ownerResolver.currentOwnerId(),
                file.getOriginalFilename(), file.getContentType(), readBytes(file));
        return ResponseEntity
                .created(URI.create("/api/v1/documents/" + document.getPublicId()))
                .body(DocumentResponse.from(document));
    }

    @GetMapping("/{publicId}")
    public DocumentResponse get(@PathVariable String publicId) {
        return DocumentResponse.from(
                documentApplicationService.findByPublicId(ownerResolver.currentOwnerId(), publicId));
    }

    @GetMapping
    public Page<DocumentResponse> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return documentApplicationService
                .findAll(ownerResolver.currentOwnerId(), status, pageable)
                .map(DocumentResponse::from);
    }

    /** 추출된 전체 텍스트. 길이가 커질 수 있어 별도 엔드포인트로 분리했다. */
    @GetMapping(value = "/{publicId}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text(@PathVariable String publicId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                .body(documentApplicationService.extractedTextOf(
                        ownerResolver.currentOwnerId(), publicId));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidDocumentException("업로드한 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }
}
