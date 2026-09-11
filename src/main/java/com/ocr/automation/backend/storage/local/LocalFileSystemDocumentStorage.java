package com.ocr.automation.backend.storage.local;

import com.ocr.automation.backend.storage.DocumentStorage;
import com.ocr.automation.backend.storage.DocumentStorageException;
import com.ocr.automation.backend.storage.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 로컬 파일시스템 스토리지 어댑터.
 *
 * <p>키 형식은 {@code yyyy/MM/dd/{uuid}{확장자}} 이며 base-path 기준 상대 경로다.
 * 날짜로 나눠 한 디렉터리에 파일이 무한히 쌓이는 것을 막는다.
 *
 * <p>원본 파일명은 키에 넣지 않는다. 경로 조작(`../`)과 파일명 충돌을
 * 애초에 만들지 않기 위해서이며, 원본 파일명은 DB 에만 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileSystemDocumentStorage implements DocumentStorage {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties properties;

    private Path baseDirectory;

    @PostConstruct
    void prepareBaseDirectory() {
        this.baseDirectory = Path.of(properties.basePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDirectory);
            log.info("문서 스토리지 경로: {}", baseDirectory);
        } catch (IOException e) {
            throw new DocumentStorageException(
                    "스토리지 디렉터리를 만들 수 없습니다: " + baseDirectory, e);
        }
    }

    @Override
    public String store(String originalFilename, byte[] content) {
        String storageKey = "%s/%s%s".formatted(
                LocalDate.now().format(DATE_PATH),
                UUID.randomUUID(),
                extensionOf(originalFilename));
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return storageKey;
        } catch (IOException e) {
            throw new DocumentStorageException("문서 저장에 실패했습니다: " + storageKey, e);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        Path source = resolve(storageKey);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new DocumentStorageException("문서를 읽을 수 없습니다: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("문서 삭제 실패: {}", storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    /** base-path 를 벗어나는 키는 거부한다. */
    private Path resolve(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new DocumentStorageException("스토리지 키가 비어 있습니다");
        }
        Path resolved;
        try {
            resolved = baseDirectory.resolve(storageKey).normalize();
        } catch (InvalidPathException e) {
            throw new DocumentStorageException("잘못된 스토리지 키입니다: " + storageKey, e);
        }
        if (!resolved.startsWith(baseDirectory)) {
            throw new DocumentStorageException("허용 범위를 벗어난 스토리지 키입니다: " + storageKey);
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot);
        // 확장자에 경로 구분자나 이상한 문자가 섞여 들어오는 경우를 막는다
        return extension.matches("\\.[A-Za-z0-9]{1,10}") ? extension.toLowerCase() : "";
    }
}
