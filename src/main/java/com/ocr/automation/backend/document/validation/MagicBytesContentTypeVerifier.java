package com.ocr.automation.backend.document.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 매직 바이트로 형식을 확인한다.
 *
 * <h2>왜 Apache Tika 를 쓰지 않았나</h2>
 * Tika 는 수백 가지 형식을 다루는 대신 의존성과 기동 시간이 무겁다.
 * 여기서 허용하는 형식은 넷뿐이고 각각의 시그니처는 몇 바이트짜리 상수다.
 * 형식이 열 가지를 넘어가면 그때 Tika 를 검토한다.
 *
 * <p>PDF 는 시그니처 확인에서 끝나지 않는다. 암호화됐거나 지나치게 긴 문서는
 * 통과시켜봐야 OCR 단계에서 실패하거나 워커를 오래 붙잡으므로
 * {@link PdfInspector} 가 업로드 시점에 함께 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MagicBytesContentTypeVerifier implements ContentTypeVerifier {

    private final PdfInspector pdfInspector;

    @Override
    public void verify(String declaredContentType, byte[] content) {
        FileSignature declared = FileSignature.of(declaredContentType);
        if (declared == null) {
            // 허용 목록 검사는 앞 단계에서 이미 했다. 여기 오면 목록과 이 enum 이 어긋난 것이다.
            throw new ContentMismatchException(
                    "검증할 수 없는 형식입니다: " + declaredContentType);
        }

        if (!declared.matches(content)) {
            FileSignature actual = FileSignature.detect(content);
            log.warn("형식이 일치하지 않는 업로드: declared={}, actual={}",
                    declaredContentType, actual == null ? "알 수 없음" : actual.contentType());
            throw new ContentMismatchException(
                    "파일 내용이 %s 형식이 아닙니다%s".formatted(
                            declaredContentType,
                            actual == null ? "" : " (실제: %s)".formatted(actual.contentType())));
        }

        if (declared == FileSignature.PDF) {
            pdfInspector.inspect(content);
        }
    }
}
