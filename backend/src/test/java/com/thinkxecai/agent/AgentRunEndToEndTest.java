package com.thinkxecai.agent;

import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * From {@code POST /chat-messages} to the saved answer, with the real listener, scheduler, executor, and
 * database and a scripted model. Rows are committed (runs read them on other threads), so they are removed
 * afterwards.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentRunEndToEndTest extends ServiceTestSupport {

    private static final String MARKER = "agent-run-end-to-end-test";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunner runner;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ChatClientRegistry chatClientRegistry;

    private final ScriptedChatModel model = new ScriptedChatModel();
    private UUID agent;
    private UUID session;

    @BeforeEach
    void agentAndSession() {
        when(chatClientRegistry.get("deepseek-chat")).thenReturn(ChatClient.builder(model).build());
        agent = insertAgent(MARKER);
        session = insertChatSession(agent, MARKER);
    }

    @AfterEach
    void removeCommittedRows() {
        jdbcTemplate.update("DELETE FROM chat_session WHERE title = ?", MARKER);
        jdbcTemplate.update("DELETE FROM agent WHERE name = ?", MARKER);
    }

    private void send(String content) throws Exception {
        mockMvc.perform(post("/api/chat-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + session + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> contents() {
        return jdbcTemplate.queryForList(
                "SELECT role || ': ' || content FROM chat_message WHERE session_id = ? ORDER BY created_at", String.class, session);
    }

    private void awaitRows(int count) {
        await().atMost(WAIT).until(() -> contents().size() == count);
    }

    @Test
    void aPostedMessageIsAnswered() throws Exception {
        model.thenReply("Hi, how can I help?");

        send("Hello");

        awaitRows(2);
        assertThat(contents()).containsExactly("user: Hello", "assistant: Hi, how can I help?");
    }

    @Test
    void twoMessagesSentAtOnceAreAnsweredOneAfterTheOther() throws Exception {
        CountDownLatch secondSaved = new CountDownLatch(1);
        model.then(() -> {
            try {
                assertThat(secondSaved.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return ScriptedChatModel.reply("First answer.");
        }).thenReply("Second answer.");

        send("first");
        await().atMost(WAIT).until(() -> model.prompts.size() == 1);
        send("second");
        secondSaved.countDown();

        awaitRows(4);
        assertThat(contents()).containsExactly(
                "user: first", "user: second", "assistant: First answer.", "assistant: Second answer.");
        // The second run started after the first had saved its answer, and gets its own question last (ADR 046).
        assertThat(model.prompts.get(1).getInstructions().stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(Message::getText))
                .containsExactly("first", "First answer.", "second");
    }

    @Test
    void aRunRejectedInTheCommitCallbackSavesItsNotice() {
        // what the listener does when the executor is full: submit from the commit callback of the
        // message's transaction, whose connection is still bound to the thread at that point
        AgentRunScheduler full = new AgentRunScheduler(runner, task -> {
            throw new TaskRejectedException("queue full");
        }, transactionManager);

        AtomicInteger committedOnReturn = new AtomicInteger(-1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID hello = jdbcTemplate.queryForObject(
                    "INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', 'Hello') RETURNING id", UUID.class, session);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int completion) {
                    full.submit(agent, session, hello);
                    // Another thread reads through another connection: it sees only committed rows. Joining the
                    // finished transaction would leave the notice to whatever happens to that connection next.
                    committedOnReturn.set(CompletableFuture.supplyAsync(() -> countRows(
                            "chat_message", "session_id = ? AND content = ?", session, AgentNotices.BUSY)).join());
                }
            });
        });

        assertThat(committedOnReturn).hasValue(1);
        assertThat(contents()).containsExactly("user: Hello", "assistant: " + AgentNotices.BUSY);
        assertThat(model.prompts).isEmpty();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aRejectedRunOfADeletedSessionSavesNothingAndIsNoError(CapturedOutput output) {
        AgentRunScheduler full = new AgentRunScheduler(runner, task -> {
            throw new TaskRejectedException("queue full");
        }, transactionManager);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", session);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int completion) {
                    full.submit(agent, session, UUID.randomUUID());
                }
            });
        });

        // a save joined to a transaction would mark it rollback-only, and its commit would fail
        assertThat(output).contains("Notice not saved: session " + session + " was deleted")
                .doesNotContain("Notice not saved for session");
    }

}
