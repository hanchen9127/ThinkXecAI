package com.thinkxecai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

/**
 * Files cannot join a database transaction, so deleting a document or knowledge base removes its
 * files only after the commit (ADR 026). Every test runs without the test transaction, because only
 * a real commit or rollback fires the cleanup; committed rows are removed afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeleteFileCleanupTest extends ServiceTestSupport {

    private static final Path STORAGE = createStorageDirectory();

    @Autowired
    private DocumentFacadeService documentFacadeService;

    @Autowired
    private KnowledgeBaseFacadeService knowledgeBaseFacadeService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private DocumentStorageService documentStorageService;

    private final List<UUID> knowledgeBases = new ArrayList<>();

    @DynamicPropertySource
    static void useTemporaryStorage(DynamicPropertyRegistry registry) {
        registry.add("thinkxecai.document.storage-path", STORAGE::toString);
    }

    @AfterEach
    void removeCommittedRowsAndFiles() throws IOException {
        for (UUID kb : knowledgeBases) {
            jdbcTemplate.update("DELETE FROM knowledge_base WHERE id = ?", kb);
        }
        FileSystemUtils.deleteRecursively(STORAGE);
        Files.createDirectories(STORAGE);
    }

    @Test
    void deletingADocumentRemovesItsFilesAndKeepsTheOthers() {
        UUID kb = knowledgeBase();
        UUID document = insertDocument(kb, "a.md");
        Path file = storeFile(kb, document);
        Path sibling = storeFile(kb, insertDocument(kb, "b.md"));

        documentFacadeService.deleteDocument(document);

        assertThat(file.getParent()).doesNotExist();
        assertThat(sibling).exists();
    }

    @Test
    void deletingAKnowledgeBaseRemovesItsDirectoryAndKeepsOtherKnowledgeBases() {
        UUID kb = knowledgeBase();
        storeFile(kb, insertDocument(kb, "a.md"));
        storeFile(kb, insertDocument(kb, "b.md"));
        UUID otherKb = knowledgeBase();
        Path otherFile = storeFile(otherKb, insertDocument(otherKb, "c.md"));

        knowledgeBaseFacadeService.deleteKnowledgeBase(kb);

        assertThat(STORAGE.resolve(kb.toString())).doesNotExist();
        assertThat(otherFile).exists();
    }

    @Test
    void aRolledBackDocumentDeleteKeepsTheDocumentAndItsFiles() {
        UUID kb = knowledgeBase();
        UUID document = insertDocument(kb, "a.md");
        Path file = storeFile(kb, document);

        transactionTemplate.executeWithoutResult(status -> {
            documentFacadeService.deleteDocument(document);
            status.setRollbackOnly();
        });

        assertThat(countRows("document", "id = ?", document)).isOne();
        assertThat(file).exists();
    }

    @Test
    void aRolledBackKnowledgeBaseDeleteKeepsItsFiles() {
        UUID kb = knowledgeBase();
        Path file = storeFile(kb, insertDocument(kb, "a.md"));

        transactionTemplate.executeWithoutResult(status -> {
            knowledgeBaseFacadeService.deleteKnowledgeBase(kb);
            status.setRollbackOnly();
        });

        assertThat(countRows("knowledge_base", "id = ?", kb)).isOne();
        assertThat(file).exists();
    }

    @Test
    void aFailedFileDeletionDoesNotFailTheDelete() {
        UUID kb = knowledgeBase();
        UUID document = insertDocument(kb, "a.md");
        Path file = storeFile(kb, document);
        doThrow(new UncheckedIOException(new IOException("file is locked")))
                .when(documentStorageService).deleteDocumentFiles(kb, document);

        documentFacadeService.deleteDocument(document);

        assertThat(countRows("document", "id = ?", document)).isZero();
        assertThat(file).exists();
    }

    private UUID knowledgeBase() {
        UUID kb = insertKnowledgeBase("kb");
        knowledgeBases.add(kb);
        return kb;
    }

    private Path storeFile(UUID kb, UUID document) {
        return STORAGE.resolve(documentStorageService.save(kb, document, "md", "# A\nb".getBytes(StandardCharsets.UTF_8)));
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("thinkxecai-delete-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
