package com.ocr.automation.backend.ocr.tesseract;

import com.ocr.automation.backend.ocr.OcrDocumentSource;
import com.ocr.automation.backend.ocr.OcrEngine;
import com.ocr.automation.backend.ocr.OcrEngineException;
import com.ocr.automation.backend.ocr.OcrEngineProperties;
import com.ocr.automation.backend.ocr.OcrExtraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tesseract(tess4j) 기반 OCR 어댑터.
 *
 * <p><b>네이티브 의존</b>: 실행 환경에 tesseract 공유 라이브러리와 tessdata 가
 * 설치되어 있어야 한다. 없으면 {@link UnsatisfiedLinkError} 가 나므로
 * 이를 잡아 {@link OcrEngineException} 으로 바꿔 재시도 정책에 태운다.
 *
 * <p><b>스레드 안전성</b>: {@link Tesseract} 인스턴스는 스레드 안전하지 않다.
 * 빈으로 공유하지 않고 호출마다 새로 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ocr.engine.type", havingValue = "tesseract", matchIfMissing = true)
public class TesseractOcrEngine implements OcrEngine {

    private static final String ENGINE_NAME = "tesseract";

    private final OcrEngineProperties properties;

    @Override
    public String name() {
        return ENGINE_NAME;
    }

    @Override
    public OcrExtraction extract(OcrDocumentSource source) {
        // tess4j 는 파일 기반 API 가 가장 안정적이므로 임시 파일로 떨어뜨린다.
        Path tempFile = writeTempFile(source);
        try {
            String text = newTesseract().doOCR(tempFile.toFile());
            return new OcrExtraction(
                    text,
                    null, // 신뢰도는 아직 수집하지 않는다 (아래 주석 참고)
                    source.isPdf() ? null : 1,
                    properties.tesseract().language());
        } catch (TesseractException e) {
            throw new OcrEngineException(
                    "Tesseract 인식에 실패했습니다: " + source.filename(), e);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new OcrEngineException(
                    "Tesseract 네이티브 라이브러리를 찾을 수 없습니다. "
                            + "설치하거나 ocr.engine.type=stub 으로 전환하세요.", e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private ITesseract newTesseract() {
        OcrEngineProperties.Tesseract config = properties.tesseract();
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(config.dataPath());
        tesseract.setLanguage(config.language());
        tesseract.setOcrEngineMode(config.engineMode());
        tesseract.setPageSegMode(config.pageSegmentationMode());
        return tesseract;
    }

    private Path writeTempFile(OcrDocumentSource source) {
        try {
            Path tempFile = Files.createTempFile("ocr-", source.extension());
            Files.write(tempFile, source.content());
            return tempFile;
        } catch (IOException e) {
            throw new OcrEngineException("OCR 임시 파일 생성에 실패했습니다", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("OCR 임시 파일 삭제 실패: {}", path, e);
        }
    }
}
