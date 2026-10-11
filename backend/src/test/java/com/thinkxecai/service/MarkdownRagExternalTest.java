package com.thinkxecai.service;

import com.thinkxecai.model.vo.DocumentVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Upload and search with the real local Ollama bge-m3 (needs Ollama running with bge-m3 pulled). */
@Tag("external")
class MarkdownRagExternalTest extends ServiceTestSupport {

    private static final String GUIDE = """
            # Installation
            Download the release archive and unzip it.

            # Database configuration
            Start PostgreSQL with `docker compose up -d`; it listens on port 5433.

            # Troubleshooting
            If the page stays blank, clear the browser cache.
            """;

    @Autowired
    private DocumentFacadeService documentFacadeService;

    @Autowired
    private RagService ragService;

    @DynamicPropertySource
    static void useTemporaryStorage(DynamicPropertyRegistry registry) {
        try {
            String storage = Files.createTempDirectory("thinkxecai-rag-external").toString();
            registry.add("thinkxecai.document.storage-path", () -> storage);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void anUploadedSectionIsFoundByAQuestionAboutIt() {
        UUID kb = insertKnowledgeBase("guide");
        DocumentVO document = documentFacadeService.uploadDocument(kb, "guide.md", GUIDE.getBytes(StandardCharsets.UTF_8));

        assertThat(countRows("chunk_bge_m3", "doc_id = ?", UUID.fromString(document.getId()))).isEqualTo(3);
        assertThat(ragService.search(kb, "How do I set up PostgreSQL?"))
                .hasSize(3)
                .first().isEqualTo("Start PostgreSQL with `docker compose up -d`; it listens on port 5433.");
    }

}
