package com.ocr.automation.backend.document.validation;

import com.ocr.automation.backend.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDF 를 업로드 시점에 열어 본다.
 *
 * <p>여기서 걸러야 하는 이유: 통과시키면 <b>OCR 단계에서야 문제가 드러난다.</b>
 * 그때는 이미 워커 하나가 붙잡혀 있고, 실패 문서가 재시도 한도만큼 쌓인 뒤에야
 * FAILED 로 확정된다. 업로드 응답으로 바로 알려주는 편이 낫다.
 *
 * <ul>
 *   <li><b>암호화된 PDF</b> — OCR 이 열지 못한다</li>
 *   <li><b>지나치게 긴 PDF</b> — 1000쪽 문서 하나가 워커를 몇 시간 점유할 수 있다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfInspector {

    private final StorageProperties storageProperties;

    public void inspect(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new ContentMismatchException("암호화된 PDF 는 처리할 수 없습니다");
            }
            int pages = document.getNumberOfPages();
            if (pages > storageProperties.maxPdfPages()) {
                throw new ContentMismatchException(
                        "페이지가 너무 많습니다: %d쪽 (최대 %d쪽)"
                                .formatted(pages, storageProperties.maxPdfPages()));
            }
        } catch (ContentMismatchException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 열리지 않는 PDF 는 시그니처만 맞을 뿐 내용이 깨진 것이다.
            // 암호 때문에 못 여는 경우도 여기로 온다.
            log.debug("PDF 를 열 수 없습니다", e);
            throw new ContentMismatchException("PDF 를 읽을 수 없습니다: 손상되었거나 암호화된 파일입니다");
        }
    }
}
