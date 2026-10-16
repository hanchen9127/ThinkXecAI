package com.thinkxecai.service;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.ChunkMapper;
import com.thinkxecai.model.entity.ChunkEntity;
import com.thinkxecai.model.vo.DocumentVO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Upload end to end against the real database and a temporary storage directory; only the embedding model is mocked. */
class DocumentUploadServiceTest extends ServiceTestSupport {

    private static final Path STORAGE = createStorageDirectory();
    private static final String MARKDOWN = "intro\n\n# Install\nRun `mvn test`.\n\n## Usage\n| a | b |\n|---|---|\n| 1 | 2 |\n";
    private static final List<String> TITLES = List.of("Install", "Usage");

    @Autowired
    private DocumentFacadeService documentFacadeService;

    @Autowired
    private ChunkMapper chunkMapper;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void useTemporaryStorage(DynamicPropertyRegistry registry) {
        registry.add("thinkxecai.document.storage-path", STORAGE::toString);
    }

    @Test
    void storesTheDocumentItsFileAndOneChunkPerSection() throws IOException {
        UUID kb = insertKnowledgeBase("kb");
        when(embeddingModel.embed(TITLES)).thenReturn(List.of(vector(0), vector(1)));

        DocumentVO document = documentFacadeService.uploadDocument(kb, "guide.md", bytes(MARKDOWN));

        assertThat(document.getName()).isEqualTo("guide.md");
        assertThat(document.getKbId()).isEqualTo(kb.toString());
        String filePath = (String) document.getMetadata().get("filePath");
        assertThat(filePath).matches(kb + "/" + document.getId() + "/[0-9a-f-]{36}\\.md");
        assertThat(Files.readString(STORAGE.resolve(filePath))).isEqualTo(MARKDOWN);

        List<ChunkEntity> chunks = jdbcTemplate.query("SELECT id, kb_id, title, content FROM chunk_bge_m3 WHERE doc_id = ?",
                new BeanPropertyRowMapper<>(ChunkEntity.class), UUID.fromString(document.getId()));
        assertThat(chunks).extracting(ChunkEntity::getTitle).containsExactlyInAnyOrderElementsOf(TITLES);
        assertThat(chunks).filteredOn(chunk -> chunk.getTitle().equals("Usage"))
                .extracting(ChunkEntity::getContent).containsExactly("| a | b |\n|---|---|\n| 1 | 2 |");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getKbId()).isEqualTo(kb));
        assertThat(chunkMapper.selectById(chunks.get(0).getId()).getEmbedding()).hasSize(1024);
        verify(embeddingModel).embed(TITLES);
    }

    @Test
    void keepsOnlyTheFileNameAndStoresTheExtensionFromTheAllowlistInLowerCase() {
        UUID kb = insertKnowledgeBase("kb");
        when(embeddingModel.embed(List.of("Install"))).thenReturn(List.of(vector(0)));

        DocumentVO document = documentFacadeService.uploadDocument(kb, "C:\\docs\\../Guide.MARKDOWN", bytes("# Install\nx"));

        assertThat(document.getName()).isEqualTo("Guide.MARKDOWN");
        assertThat((String) document.getMetadata().get("filePath")).endsWith(".markdown");
    }

    @Test
    void refusesAnUnknownKnowledgeBase() {
        assertRefusedBeforeAnyWork(UUID.randomUUID(), "guide.md", bytes(MARKDOWN), "kbId: unknown knowledge base");
    }

    @Test
    void refusesFilesThatAreNotMarkdown() {
        UUID kb = insertKnowledgeBase("kb");
        for (String name : new String[]{"notes.txt", "README", ".md", "guide.md.exe", "", null}) {
            assertRefusedBeforeAnyWork(kb, name, bytes(MARKDOWN), "file: only Markdown files");
        }
    }

    @Test
    void acceptsAFileNameOf200CharactersNotCountingTheClientPath() {
        UUID kb = insertKnowledgeBase("kb");
        when(embeddingModel.embed(List.of("Install"))).thenReturn(List.of(vector(0)));
        String longest = "a".repeat(197) + ".md";

        DocumentVO document = documentFacadeService.uploadDocument(kb, "C:\\docs\\" + longest, bytes("# Install\nx"));

        assertThat(document.getName()).isEqualTo(longest);
    }

    @Test
    void refusesAFileNameLongerThan200Characters() {
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "a".repeat(198) + ".md", bytes(MARKDOWN),
                "file: name is longer than 200 characters");
    }

    @Test
    void acceptsAtMost500SectionsAndEmbedsThemInOneCall() {
        UUID kb = insertKnowledgeBase("kb");
        // Distinct vectors away from RagServiceTest's query point: rolled-back rows stay in the HNSW index until
        // VACUUM, and 500 copies of one vector there crowded that test's small knowledge base out of the scan.
        when(embeddingModel.embed(anyList())).thenAnswer(call -> IntStream.range(0, ((List<?>) call.getArgument(0)).size())
                .mapToObj(i -> vector(100 + i)).toList());

        DocumentVO document = documentFacadeService.uploadDocument(kb, "guide.md", bytes("# s\nx\n".repeat(500)));

        assertThat(countRows("chunk_bge_m3", "doc_id = ?", UUID.fromString(document.getId()))).isEqualTo(500);
        verify(embeddingModel).embed(anyList());
    }

    @Test
    void refusesMoreThan500Sections() {
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", bytes("# s\n".repeat(501)),
                "file: has more than 500 sections");
    }

    @Test
    void refusesNulCharacters() {
        // valid UTF-8, but PostgreSQL cannot store it; refused before the embedding call instead of failing after it
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", bytes("# A\na\u0000b"),
                "file: must not contain NUL characters");
    }

    @Test
    void refusesADocumentNestedTooDeeplyToParse() {
        // 20,000 nested list markers overflow the parser's stack (10,000 already do)
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", bytes("# A\n" + "- ".repeat(20_000) + "x"),
                "file: is nested too deeply to read");
    }

    @Test
    void refusesAnEmptyFile() {
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", new byte[0], "file: is empty");
    }

    @Test
    void refusesTextThatIsNotUtf8() {
        byte[] gbk = "# 安装\n内容".getBytes(Charset.forName("GBK"));

        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", gbk, "file: must be UTF-8");
    }

    @Test
    void refusesMarkdownWithoutAHeadingWithText() {
        assertRefusedBeforeAnyWork(insertKnowledgeBase("kb"), "guide.md", bytes("text only\n\n#\n"), "file: has no heading with text");
    }

    @Test
    void anEmbeddingFailureLeavesNothingBehind() {
        UUID kb = insertKnowledgeBase("kb");
        when(embeddingModel.embed(TITLES)).thenThrow(new IllegalStateException("Ollama is down"));

        assertThatThrownBy(() -> documentFacadeService.uploadDocument(kb, "guide.md", bytes(MARKDOWN)))
                .hasMessage("Ollama is down");

        assertNothingStored(kb);
    }

    /** Runs without the test transaction: only a real rollback triggers the file cleanup. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFailureAfterTheFileIsSavedRollsBackTheRowsAndDeletesTheFile() {
        UUID kb = insertKnowledgeBase("kb");
        try {
            // A 3-dimension vector is accepted by the mock but rejected by the VECTOR(1024) column,
            // so the chunk insert fails after the document row and the file have been written.
            when(embeddingModel.embed(TITLES)).thenReturn(List.of(new float[3], new float[3]));

            assertThatThrownBy(() -> documentFacadeService.uploadDocument(kb, "guide.md", bytes(MARKDOWN)))
                    .hasMessageContaining("1024");

            assertNothingStored(kb);
        } finally {
            deleteCommittedKnowledgeBase(kb);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aCommittedUploadEmbedsBeforeItsTransactionAndKeepsItsFile() {
        UUID kb = insertKnowledgeBase("kb");
        try {
            AtomicBoolean transactionDuringEmbedding = new AtomicBoolean(true);
            when(embeddingModel.embed(TITLES)).thenAnswer(invocation -> {
                transactionDuringEmbedding.set(TransactionSynchronizationManager.isActualTransactionActive());
                return List.of(vector(0), vector(1));
            });

            DocumentVO document = documentFacadeService.uploadDocument(kb, "guide.md", bytes(MARKDOWN));

            assertThat(transactionDuringEmbedding).isFalse();
            assertThat(STORAGE.resolve((String) document.getMetadata().get("filePath"))).exists();
            assertThat(countRows("chunk_bge_m3", "kb_id = ?", kb)).isEqualTo(2);
        } finally {
            deleteCommittedKnowledgeBase(kb);
        }
    }

    private void assertRefusedBeforeAnyWork(UUID kbId, String fileName, byte[] content, String message) {
        assertThatThrownBy(() -> documentFacadeService.uploadDocument(kbId, fileName, content))
                .isInstanceOf(BizException.class)
                .hasMessageStartingWith(message)
                .extracting("code").isEqualTo(400);
        verify(embeddingModel, never()).embed(anyList());
        assertNothingStored(kbId);
    }

    private void assertNothingStored(UUID kbId) {
        assertThat(countRows("document", "kb_id = ?", kbId)).isZero();
        assertThat(countRows("chunk_bge_m3", "kb_id = ?", kbId)).isZero();
        assertThat(filesUnder(STORAGE.resolve(kbId.toString()))).isEmpty();
    }

    /** Rows committed by a test without the test transaction; chunks and documents go by cascade. */
    private void deleteCommittedKnowledgeBase(UUID kbId) {
        jdbcTemplate.update("DELETE FROM knowledge_base WHERE id = ?", kbId);
        try {
            FileSystemUtils.deleteRecursively(STORAGE.resolve(kbId.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> filesUnder(Path directory) {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static float[] vector(int dimension) {
        float[] v = new float[1024];
        v[dimension] = 1f;
        return v;
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("thinkxecai-upload-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
