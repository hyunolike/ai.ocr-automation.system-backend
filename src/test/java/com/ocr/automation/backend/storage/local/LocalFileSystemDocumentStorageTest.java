package com.ocr.automation.backend.storage.local;

import com.ocr.automation.backend.storage.DocumentStorageException;
import com.ocr.automation.backend.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemDocumentStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileSystemDocumentStorage storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                tempDir.toString(), 20971520L, List.of("image/png"), 200);
        storage = new LocalFileSystemDocumentStorage(properties);
        storage.prepareBaseDirectory();
    }

    @Test
    void 저장하고_다시_읽는다() {
        byte[] content = "hello ocr".getBytes(StandardCharsets.UTF_8);

        String key = storage.store("scan.png", content);

        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.read(key)).isEqualTo(content);
    }

    @Test
    void 키는_날짜_경로와_확장자를_가진다() {
        String key = storage.store("scan.PNG", new byte[]{1, 2, 3});

        assertThat(key).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png");
    }

    @Test
    void 같은_파일명을_여러번_저장해도_덮어쓰지_않는다() {
        String first = storage.store("scan.png", new byte[]{1});
        String second = storage.store("scan.png", new byte[]{2});

        assertThat(first).isNotEqualTo(second);
        assertThat(storage.read(first)).isEqualTo(new byte[]{1});
        assertThat(storage.read(second)).isEqualTo(new byte[]{2});
    }

    @Test
    void 원본_파일명은_키에_넣지_않는다() {
        String key = storage.store("../../etc/passwd.png", new byte[]{1});

        assertThat(key).doesNotContain("passwd");
        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    void 이상한_확장자는_떼어낸다() {
        String key = storage.store("scan.p/../ng", new byte[]{1});

        assertThat(key).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}");
    }

    @Test
    void base_경로를_벗어나는_키는_거부한다() {
        assertThatThrownBy(() -> storage.read("../../../etc/passwd"))
                .isInstanceOf(DocumentStorageException.class)
                .hasMessageContaining("허용 범위를 벗어난");
    }

    @Test
    void 빈_키는_거부한다() {
        assertThatThrownBy(() -> storage.read("  "))
                .isInstanceOf(DocumentStorageException.class)
                .hasMessageContaining("비어 있습니다");
    }

    @Test
    void 삭제하면_존재하지_않는다() {
        String key = storage.store("scan.png", new byte[]{1});

        storage.delete(key);

        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void 없는_키를_삭제해도_예외가_나지_않는다() {
        storage.delete("2026/01/01/nope.png");
    }

    @Test
    void 없는_키를_읽으면_예외가_난다() {
        assertThatThrownBy(() -> storage.read("2026/01/01/nope.png"))
                .isInstanceOf(DocumentStorageException.class)
                .hasMessageContaining("읽을 수 없습니다");
    }
}
