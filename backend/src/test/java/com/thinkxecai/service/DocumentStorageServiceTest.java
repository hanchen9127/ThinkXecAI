package com.thinkxecai.service;

import com.thinkxecai.service.impl.DocumentStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageServiceTest {

    private static final byte[] CONTENT = "# Title\n\nBody".getBytes(StandardCharsets.UTF_8);

    @TempDir
    private Path base;

    private Path root;
    private DocumentStorageService storage;

    private final UUID kbId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        root = base.resolve("documents");
        storage = new DocumentStorageServiceImpl(root.toString());
    }

    @Test
    void savesUnderKbAndDocumentDirectoriesAndReturnsTheRelativePath() throws IOException {
        String relativePath = storage.save(kbId, documentId, "md", CONTENT);

        assertThat(relativePath).matches(kbId + "/" + documentId + "/[0-9a-f-]{36}\\.md");
        assertThat(Files.readAllBytes(root.resolve(relativePath))).isEqualTo(CONTENT);
    }

    @Test
    void eachSaveGetsItsOwnFileName() {
        String first = storage.save(kbId, documentId, "md", CONTENT);
        String second = storage.save(kbId, documentId, "md", CONTENT);

        assertThat(first).isNotEqualTo(second);
        assertThat(root.resolve(first)).exists();
        assertThat(root.resolve(second)).exists();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "../x", "md/../../../../evil", "..\\..\\evil", "/etc/passwd", "md:stream", "%2e%2e", "MD", "md "})
    void refusesExtensionsThatAreNotPlainLowercaseAlphanumeric(String extension) throws IOException {
        assertThatThrownBy(() -> storage.save(kbId, documentId, extension, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);

        try (Stream<Path> written = Files.walk(base)) {
            assertThat(written.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void deletingADocumentRemovesOnlyItsDirectory() {
        String deleted = storage.save(kbId, documentId, "md", CONTENT);
        String kept = storage.save(kbId, UUID.randomUUID(), "md", CONTENT);

        storage.deleteDocumentFiles(kbId, documentId);

        assertThat(root.resolve(kbId.toString()).resolve(documentId.toString())).doesNotExist();
        assertThat(root.resolve(deleted)).doesNotExist();
        assertThat(root.resolve(kept)).exists();
    }

    @Test
    void deletingAKnowledgeBaseRemovesOnlyItsDirectory() {
        storage.save(kbId, documentId, "md", CONTENT);
        storage.save(kbId, UUID.randomUUID(), "md", CONTENT);
        UUID otherKbId = UUID.randomUUID();
        String kept = storage.save(otherKbId, UUID.randomUUID(), "md", CONTENT);

        storage.deleteKnowledgeBaseFiles(kbId);

        assertThat(root.resolve(kbId.toString())).doesNotExist();
        assertThat(root.resolve(kept)).exists();
    }

    @Test
    void deletingDirectoriesThatDoNotExistIsANoOp() {
        assertThatCode(() -> {
            storage.deleteDocumentFiles(kbId, documentId);
            storage.deleteKnowledgeBaseFiles(kbId);
        }).doesNotThrowAnyException();
        assertThat(root).doesNotExist();
    }

}
