package com.thinkxecai.agent;

import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.RagService;
import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.thinkxecai.agent.ScriptedChatModel.call;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Think-Execute loop against the real database and the real tools, with a scripted model in place
 * of DeepSeek and Gemini: what it saves, what the model sees, and how a run that cannot finish ends.
 */
class AgentRunnerTest extends ServiceTestSupport {

    private static final String MODEL = "deepseek-chat";

    @Autowired
    private AgentRunner runner;

    @MockitoBean
    private ChatClientRegistry chatClientRegistry;

    @MockitoBean
    private GeneratedMessagePublisher publisher;

    @MockitoBean
    private RagService ragService;

    private final ScriptedChatModel model = new ScriptedChatModel();
    private UUID agent;
    private UUID session;

    @BeforeEach
    void agentAndSession() {
        when(chatClientRegistry.get(MODEL)).thenReturn(ChatClient.builder(model).build());
        agent = insertAgent("Helper");
        session = insertChatSession(agent, "chat");
    }

    private void userSays(String text) {
        jdbcTemplate.update("INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', ?)", session, text);
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("SELECT role, content, metadata::text AS metadata FROM chat_message "
                + "WHERE session_id = ? ORDER BY created_at", session);
    }

    private List<String> roles() {
        return rows().stream().map(row -> (String) row.get("role")).toList();
    }

    private String lastContent() {
        List<Map<String, Object>> rows = rows();
        return (String) rows.get(rows.size() - 1).get("content");
    }

    private Prompt prompt(int index) {
        return model.prompts.get(index);
    }

    @Test
    void aPlainAnswerIsSavedAndPublished() {
        userSays("Hello");
        model.thenReply("Hi, how can I help?");

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo("Hi, how can I help?");
        verify(publisher).publish(eq(session), any(ChatMessageVO.class));
    }

    @Test
    void aToolStepSavesTheCallAndItsResultThenTheAnswer() {
        userSays("What is the date?");
        model.thenCall("Let me check.", call("", "getDate", "{}")).thenReply("Today is the date shown.");

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        String callId = jdbcTemplate.queryForObject("SELECT metadata->'toolCalls'->0->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'assistant' AND metadata IS NOT NULL", String.class, session);
        String resultId = jdbcTemplate.queryForObject("SELECT metadata->'toolResponse'->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool'", String.class, session);
        assertThat(callId).startsWith("call_").isEqualTo(resultId);
        // The second model call sees the call and its result.
        List<Message> second = prompt(1).getInstructions();
        assertThat(second.get(second.size() - 2)).isInstanceOfSatisfying(AssistantMessage.class, assistant ->
                assertThat(assistant.getToolCalls()).extracting(AssistantMessage.ToolCall::id).containsExactly(callId));
        assertThat(second.get(second.size() - 1)).isInstanceOfSatisfying(ToolResponseMessage.class, results ->
                assertThat(results.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly(callId));
        verify(publisher, times(3)).publish(eq(session), any(ChatMessageVO.class));
    }

    @Test
    void parallelCallsSaveOneResultEach() {
        userSays("Where and when?");
        model.thenCall("", call("a", "getCity", "{}"), call("b", "getDate", "{}")).thenReply("Done.");

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "tool", "assistant");
        List<String> resultIds = jdbcTemplate.queryForList("SELECT metadata->'toolResponse'->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool' ORDER BY created_at", String.class, session);
        assertThat(resultIds).containsExactly("a", "b");
    }

    @Test
    void terminateEndsTheRunAfterItsResultIsSaved() {
        userSays("Thanks, that's all.");
        model.thenCall("Glad to help.", call("t", "terminate", "{}"));

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant", "tool");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void twentyStepsWithoutAnAnswerEndWithANotice() {
        userSays("Keep checking the date.");
        for (int step = 0; step < AgentLimits.MAX_STEPS; step++) {
            model.thenCall("", call("call_" + step, "getDate", "{}"));
        }

        runner.run(agent, session);

        assertThat(model.prompts).hasSize(AgentLimits.MAX_STEPS);
        assertThat(rows()).hasSize(1 + 2 * AgentLimits.MAX_STEPS + 1);
        assertThat(lastContent()).isEqualTo(AgentNotices.MAX_STEPS);
    }

    @Test
    void aFailingModelCallEndsWithANotice() {
        userSays("Hello");
        model.then(() -> {
            throw new RuntimeException("HTTP 503 - overloaded");
        });

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.MODEL_FAILED);
    }

    @Test
    void aDeletedAgentEndsWithANotice() {
        userSays("Hello");
        jdbcTemplate.update("DELETE FROM agent WHERE id = ?", agent);

        runner.run(agent, session);

        assertThat(lastContent()).isEqualTo(AgentNotices.AGENT_GONE);
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void anUnregisteredModelEndsWithANotice() {
        userSays("Hello");
        jdbcTemplate.update("UPDATE agent SET model = 'gpt-5.4-mini' WHERE id = ?", agent);
        when(chatClientRegistry.get("gpt-5.4-mini")).thenThrow(new IllegalArgumentException("Unknown chat model"));

        runner.run(agent, session);

        assertThat(lastContent()).isEqualTo(AgentNotices.MODEL_UNAVAILABLE);
    }

    @Test
    void aDeletedSessionStopsTheRunWithoutSavingAnything() {
        userSays("Hello");
        model.thenReply("Hi");
        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", session);

        runner.run(agent, session);

        assertThat(rows()).isEmpty();
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void theModelSeesOneSystemMessageWithThePromptTheGuidanceAndTheKnowledgeBases() {
        UUID kb = insertKnowledgeBase("Product docs");
        jdbcTemplate.update("UPDATE knowledge_base SET description = 'Manuals and FAQ' WHERE id = ?", kb);
        jdbcTemplate.update("UPDATE agent SET system_prompt = 'You answer in English.', allowed_kbs = ?::jsonb WHERE id = ?",
                "[\"" + kb + "\"]", agent);
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session);

        List<Message> instructions = prompt(0).getInstructions();
        assertThat(instructions).filteredOn(message -> message.getMessageType() == MessageType.SYSTEM).hasSize(1);
        assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(instructions.get(0).getText())
                .startsWith("You answer in English.\n\n")
                .contains(RuntimeInstructions.GUIDANCE, "- " + kb + ": Product docs — Manuals and FAQ");
        assertThat(roles()).doesNotContain("system");
    }

    @Test
    void theModelGetsTheAgentsToolsAndRunsNothingItself() {
        jdbcTemplate.update("UPDATE agent SET allowed_tools = '[\"dataBaseTool\"]'::jsonb WHERE id = ?", agent);
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session);

        assertThat(prompt(0).getOptions()).isInstanceOfSatisfying(ToolCallingChatOptions.class, options -> {
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
            assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                    .contains("KnowledgeTool", "terminate", "getCity", "getDate", "weather", "databaseQuery")
                    .doesNotContain("sendEmail");
            assertThat(options.getTemperature()).isNull();
            assertThat(options.getTopP()).isNull();
        });
    }

    @Test
    void knowledgeSearchIsLimitedToTheAgentsKnowledgeBases() {
        UUID allowed = insertKnowledgeBase("Allowed");
        UUID other = insertKnowledgeBase("Other");
        jdbcTemplate.update("UPDATE agent SET allowed_kbs = ?::jsonb WHERE id = ?", "[\"" + allowed + "\"]", agent);
        when(ragService.search(allowed, "setup")).thenReturn(List.of("Install with npm."));
        userSays("How do I set it up?");
        model.thenCall("", call("k1", "KnowledgeTool", "{\"kbsId\":\"" + allowed + "\",\"query\":\"setup\"}"),
                        call("k2", "KnowledgeTool", "{\"kbsId\":\"" + other + "\",\"query\":\"setup\"}"))
                .thenReply("Install with npm.");

        runner.run(agent, session);

        List<String> results = jdbcTemplate.queryForList("SELECT content FROM chat_message WHERE session_id = ? "
                + "AND role = 'tool' ORDER BY created_at", String.class, session);
        // Spring AI serializes a tool's String result as JSON; that is what the model saw and what is stored.
        assertThat(results.get(0)).isEqualTo("\"Install with npm.\"");
        assertThat(results.get(1)).contains("is not available");
        verify(ragService).search(allowed, "setup");
        verify(ragService, never()).search(eq(other), anyString());
    }

    @Test
    void aToolTheAgentDoesNotHaveIsAnsweredWithAnErrorAndTheRunGoesOn() {
        userSays("Count the orders.");
        model.thenCall("", call("x", "databaseQuery", "{\"sql\":\"SELECT 1\"}")).thenReply("I cannot query the database.");

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        String result = jdbcTemplate.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND role = 'tool'", String.class, session);
        assertThat(result).isEqualTo("Unknown tool: databaseQuery. Use only the tools you were given.");
    }

    @Test
    void inALongSessionTheModelSeesTheNewestQuestion() {
        for (int i = 0; i < 35; i++) {
            userSays("old question " + i);
            jdbcTemplate.update("INSERT INTO chat_message (session_id, role, content) VALUES (?, 'assistant', ?)",
                    session, "old answer " + i);
        }
        userSays("the newest question");
        model.thenReply("answer");

        runner.run(agent, session);

        // The latest 10 rows start with an old answer, which sanitizing drops: memory starts at a user message.
        List<Message> instructions = prompt(0).getInstructions();
        assertThat(instructions).hasSize(1 + 9);
        assertThat(instructions.get(1)).isInstanceOf(UserMessage.class).extracting(Message::getText).isEqualTo("old question 31");
        assertThat(instructions.get(instructions.size() - 1)).isInstanceOf(UserMessage.class)
                .extracting(Message::getText).isEqualTo("the newest question");
    }

    @Test
    void aSessionWhoseLastRunDiedAfterAToolCallStillRuns() {
        userSays("What is the date?");
        jdbcTemplate.update("INSERT INTO chat_message (session_id, role, content, metadata) VALUES (?, 'assistant', '', "
                + "'{\"toolCalls\":[{\"id\":\"dead\",\"type\":\"function\",\"name\":\"getDate\",\"arguments\":\"{}\"}]}'::jsonb)",
                session);
        userSays("Hello?");
        model.thenReply("Hi");

        runner.run(agent, session);

        assertThat(prompt(0).getInstructions()).noneMatch(message ->
                message instanceof AssistantMessage assistant && assistant.hasToolCalls());
        assertThat(lastContent()).isEqualTo("Hi");
    }

    @Test
    void aFailingPublisherNeverStopsTheRun() {
        doThrow(new IllegalStateException("client gone")).when(publisher).publish(any(), any());
        userSays("What is the date?");
        model.thenCall("", call("d", "getDate", "{}")).thenReply("Done.");

        runner.run(agent, session);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
    }

}
