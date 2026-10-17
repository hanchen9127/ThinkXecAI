package com.thinkxecai.service;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Two requests at the same time (ADR 037). The first runs inside a transaction that stays open; the
 * second starts meanwhile on another thread and must wait for the first's row lock. Only then does the
 * first commit, and the second's outcome is checked. Rows are committed, so they are removed afterwards.
 * <p>
 * Not covered: deadlocks. The first request finishes all its statements before the second starts, so
 * the interleaving that would deadlock under a wrong lock order (an agent update locking the agent before
 * its knowledge bases while a knowledge-base delete runs) never happens here; the order is kept by review.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrentWritesTest extends ServiceTestSupport {

    private static final String MARKER = "concurrent-writes-test";

    @Autowired
    private AgentFacadeService agentService;

    @Autowired
    private KnowledgeBaseFacadeService knowledgeBaseService;

    @Autowired
    private ChatSessionFacadeService chatSessionService;

    @Autowired
    private ChatMessageFacadeService chatMessageService;

    @Autowired
    private DocumentFacadeService documentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void useTemporaryStorage(DynamicPropertyRegistry registry) {
        registry.add("thinkxecai.document.storage-path", () -> {
            try {
                return Files.createTempDirectory("thinkxecai-concurrent-").toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @AfterEach
    void removeCommittedRows() {
        jdbcTemplate.update("DELETE FROM agent WHERE name = ?", MARKER);
        jdbcTemplate.update("DELETE FROM knowledge_base WHERE name LIKE ? OR description = ?", MARKER + "%", MARKER);
        jdbcTemplate.update("DELETE FROM chat_session WHERE title = ?", MARKER);
    }

    @Test
    void updatesOfDifferentAgentFieldsBothSurvive() throws Exception {
        UUID agent = insertMarkedAgent();

        Throwable second = race(
                () -> agentService.updateAgent(agent, agentUpdate(request -> request.setSystemPrompt("first"))),
                () -> agentService.updateAgent(agent, agentUpdate(request -> request.setDescription(MARKER))));

        assertThat(second).isNull();
        assertThat(jdbcTemplate.queryForList("SELECT system_prompt, description FROM agent WHERE id = ?", agent))
                .containsExactly(java.util.Map.of("system_prompt", "first", "description", MARKER));
    }

    @Test
    void updatesOfDifferentKnowledgeBaseFieldsBothSurvive() throws Exception {
        UUID kb = insertKnowledgeBase(MARKER);

        Throwable second = race(
                () -> knowledgeBaseService.updateKnowledgeBase(kb, kbUpdate(MARKER + " renamed", null)),
                () -> knowledgeBaseService.updateKnowledgeBase(kb, kbUpdate(null, MARKER)));

        assertThat(second).isNull();
        assertThat(jdbcTemplate.queryForList("SELECT name, description FROM knowledge_base WHERE id = ?", kb))
                .containsExactly(java.util.Map.of("name", MARKER + " renamed", "description", MARKER));
    }

    @Test
    void aKnowledgeBaseDeletedWhileAnAgentAddsItLeavesNoDanglingId() throws Exception {
        UUID agent = insertMarkedAgent();
        UUID kb = insertKnowledgeBase(MARKER);

        Throwable second = race(
                () -> agentService.updateAgent(agent, agentUpdate(request -> request.setAllowedKbs(List.of(kb)))),
                () -> knowledgeBaseService.deleteKnowledgeBase(kb));

        assertThat(second).isNull();
        assertThat(allowedKbs(agent)).isEqualTo("[]");
    }

    @Test
    void anAgentCannotAddAKnowledgeBaseThatIsBeingDeleted() throws Exception {
        UUID agent = insertMarkedAgent();
        UUID kb = insertKnowledgeBase(MARKER);

        Throwable second = race(
                () -> knowledgeBaseService.deleteKnowledgeBase(kb),
                () -> agentService.updateAgent(agent, agentUpdate(request -> request.setAllowedKbs(List.of(kb)))));

        assertCode(second, 400);
        assertThat(allowedKbs(agent)).isEqualTo("[]");
    }

    @Test
    void aMessageToASessionBeingDeletedIsNotFound() throws Exception {
        UUID session = insertChatSession(insertMarkedAgent(), MARKER);
        CreateChatMessageRequest message = new CreateChatMessageRequest();
        message.setSessionId(session);
        message.setContent("hello");

        Throwable second = race(
                () -> chatSessionService.deleteChatSession(session),
                () -> chatMessageService.createChatMessage(message));

        assertCode(second, 404);
    }

    @Test
    void aMessageWhileTheSessionsAgentIsBeingDeletedIsAConflict() throws Exception {
        // the agent delete clears the session's agent (ON DELETE SET NULL), an update of the session row:
        // the message must wait for it, so its lock has to conflict with that update (a key-share lock would not)
        UUID agent = insertMarkedAgent();
        UUID session = insertChatSession(agent, MARKER);
        CreateChatMessageRequest message = new CreateChatMessageRequest();
        message.setSessionId(session);
        message.setContent("hello");

        Throwable second = race(() -> agentService.deleteAgent(agent), () -> chatMessageService.createChatMessage(message));

        assertCode(second, 409);
        assertThat(countRows("chat_message", "session_id = ?", session)).isZero();
    }

    @Test
    void twoMessagesToOneSessionTakeTurnsAndBothAreSaved() throws Exception {
        UUID session = insertChatSession(insertMarkedAgent(), MARKER);
        CreateChatMessageRequest message = new CreateChatMessageRequest();
        message.setSessionId(session);
        message.setContent("hello");

        Throwable second = race(() -> chatMessageService.createChatMessage(message),
                () -> chatMessageService.createChatMessage(message));

        assertThat(second).isNull();
        assertThat(countRows("chat_message", "session_id = ?", session)).isEqualTo(2);
    }

    @Test
    void aSessionForAnAgentBeingDeletedIsRefused() throws Exception {
        UUID agent = insertMarkedAgent();
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        request.setAgentId(agent);
        request.setTitle(MARKER);

        Throwable second = race(() -> agentService.deleteAgent(agent), () -> chatSessionService.createChatSession(request));

        assertCode(second, 400);
        assertThat(countRows("chat_session", "title = ?", MARKER)).isZero();
    }

    @Test
    void aDocumentForAKnowledgeBaseBeingDeletedIsRefused() throws Exception {
        UUID kb = insertKnowledgeBase(MARKER);
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setKbId(kb);
        request.setName("doc.md");

        Throwable second = race(() -> knowledgeBaseService.deleteKnowledgeBase(kb), () -> documentService.createDocument(request));

        assertCode(second, 400);
    }

    @Test
    void anUploadToAKnowledgeBaseBeingDeletedIsRefusedAndStoresNothing() throws Exception {
        UUID kb = insertKnowledgeBase(MARKER);
        float[] vector = new float[1024];
        vector[500] = 1f;
        when(embeddingModel.embed(anyList())).thenReturn(List.of(vector));
        byte[] markdown = "# Title\ntext".getBytes(StandardCharsets.UTF_8);

        Throwable second = race(() -> knowledgeBaseService.deleteKnowledgeBase(kb),
                () -> documentService.uploadDocument(kb, "doc.md", markdown));

        assertCode(second, 400);
        assertThat(countRows("document", "kb_id = ?", kb)).isZero();
    }

    /**
     * Runs {@code first} in a transaction that stays open until {@code second}, started on another
     * thread, has been waiting for half a second; then commits and returns what {@code second} threw.
     */
    private Throwable race(Runnable first, Runnable second) throws Exception {
        AtomicReference<CompletableFuture<Void>> secondRun = new AtomicReference<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            first.run();
            secondRun.set(CompletableFuture.runAsync(second));
            assertThatThrownBy(() -> secondRun.get().get(500, TimeUnit.MILLISECONDS))
                    .as("the second request must wait for the first one's lock")
                    .isInstanceOf(TimeoutException.class);
        });
        try {
            secondRun.get().get(10, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        }
    }

    private UUID insertMarkedAgent() {
        return jdbcTemplate.queryForObject(
                // every field starts non-null: the mapper skips nulls, so a stale merge must have values to write back
                "INSERT INTO agent (name, model, description, system_prompt) VALUES (?, 'deepseek-chat', 'old', 'old') RETURNING id",
                UUID.class, MARKER);
    }

    private String allowedKbs(UUID agent) {
        return jdbcTemplate.queryForObject("SELECT allowed_kbs::text FROM agent WHERE id = ?", String.class, agent);
    }

    private static UpdateAgentRequest agentUpdate(java.util.function.Consumer<UpdateAgentRequest> change) {
        UpdateAgentRequest request = new UpdateAgentRequest();
        change.accept(request);
        return request;
    }

    private static UpdateKnowledgeBaseRequest kbUpdate(String name, String description) {
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private static void assertCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

}
