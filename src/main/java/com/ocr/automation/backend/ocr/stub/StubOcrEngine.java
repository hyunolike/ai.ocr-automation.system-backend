package com.ocr.automation.backend.ocr.stub;

import com.ocr.automation.backend.ocr.OcrDocumentSource;
import com.ocr.automation.backend.ocr.OcrEngine;
import com.ocr.automation.backend.ocr.OcrExtraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 네이티브 Tesseract 가 없는 환경(로컬 개발, CI)에서 파이프라인 전체를
 * 돌려보기 위한 대체 엔진.
 *
 * <p>실제 인식은 하지 않고 입력 메타데이터를 그대로 돌려준다.
 * 파이프라인(업로드 → 스케줄 → 처리 → 조회)은 이 엔진만으로도 검증된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ocr.engine.type", havingValue = "stub")
public class StubOcrEngine implements OcrEngine {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public OcrExtraction extract(OcrDocumentSource source) {
        log.warn("StubOcrEngine 이 동작 중입니다. 실제 인식은 수행되지 않습니다: filename={}",
                source.filename());
        String text = """
                [stub-ocr] 실제 인식이 수행되지 않았습니다.
                filename=%s
                contentType=%s
                sizeBytes=%d
                """.formatted(source.filename(), source.contentType(), source.content().length);
        return new OcrExtraction(text, null, 1, "stub");
    }
}
