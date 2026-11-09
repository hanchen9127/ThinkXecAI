package com.thinkxecai.agent;

import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.tool.KnowledgeTool;
import com.thinkxecai.agent.tool.TerminateTool;
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
import java.util.function.BooleanSupplier;

import static com.thinkxecai.agent.ScriptedChatModel.call;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    /** A run nobody stops. */
    private static final BooleanSupplier NOT_STOPPED = () -> false;

    @Autowired
    private AgentRunner runner;

    @Autowired
    private AgentRuntimeFactory runtimeFactory;

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

    /** The newest user message, which each run answers, as for a run started by its chat event. */
    private UUID lastQuestion;

    private UUID userSays(String text) {
        lastQuestion = insert("user", text, null);
        return lastQuestion;
    }

    private UUID insert(String role, String content, String metadata) {
        return jdbcTemplate.queryForObject("INSERT INTO chat_message (session_id, role, content, metadata) "
                + "VALUES (?, ?, ?, ?::jsonb) RETURNING id", UUID.class, session, role, content, metadata);
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

    /** Why each stored tool result failed, in order; null for a success (ADR 064). */
    private List<String> failures() {
        return jdbcTemplate.queryForList("SELECT metadata->'toolResponse'->>'failure' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool' ORDER BY created_at", String.class, session);
    }

    private Prompt prompt(int index) {
        return model.prompts.get(index);
    }

    @Test
    void aPlainAnswerIsSavedAndPublished() {
        userSays("Hello");
        model.thenReply("Hi, how can I help?");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo("Hi, how can I help?");
        verify(publisher).publish(eq(session), any(ChatMessageVO.class));
    }

    @Test
    void aToolStepSavesTheCallAndItsResultThenTheAnswer() {
        userSays("What is the date?");
        model.thenCall("Let me check.", call("", "getDate", "{}")).thenReply("Today is the date shown.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        String callId = jdbcTemplate.queryForObject("SELECT metadata->'toolCalls'->0->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'assistant' AND metadata IS NOT NULL", String.class, session);
        String resultId = jdbcTemplate.queryForObject("SELECT metadata->'toolResponse'->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool'", String.class, session);
        assertThat(callId).startsWith("call_").isEqualTo(resultId);
        assertThat(failures()).containsExactly((String) null);
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

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "tool", "assistant");
        List<String> resultIds = jdbcTemplate.queryForList("SELECT metadata->'toolResponse'->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool' ORDER BY created_at", String.class, session);
        assertThat(resultIds).containsExactly("a", "b");
    }

    @Test
    void terminateEndsTheRunAfterItsResultIsSaved() {
        userSays("Thanks, that's all.");
        model.thenCall("Glad to help.", call("t", "terminate", "{}"));

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void terminateWithoutAnAnswerDoesNotEndTheRun() {
        // seen with Gemini: the tool results are there, but the user would get no text at all
        userSays("What is the date?");
        model.thenCall("", call("d", "getDate", "{}"))
                .thenCall("", call("t", "terminate", "{}"))
                .thenReply("Today is 2026-10-20.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant", "tool", "assistant");
        String result = jdbcTemplate.queryForObject("SELECT content FROM chat_message WHERE session_id = ? "
                + "AND metadata->'toolResponse'->>'name' = 'terminate'", String.class, session);
        assertThat(result).isEqualTo(ToolJson.result(TerminateTool.NOT_ENDED));
        // the runtime's rule declined it (ADR 045, 064); the date before it succeeded
        assertThat(failures()).containsExactly(null, "REFUSED");
        assertThat(lastContent()).isEqualTo("Today is 2026-10-20.");
        assertThat(model.prompts).hasSize(3);
        // Gemini called terminate without text again after that result: the next call offers no tools
        assertThat(toolNames(prompt(1))).contains("terminate");
        assertThat(toolNames(prompt(2))).isEmpty();
    }

    private static List<String> toolNames(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks().stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }

    private List<String> textsOf(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(message -> message instanceof ToolResponseMessage results
                        ? "tool:" + results.getResponses().get(0).id()
                        : message.getMessageType().getValue() + ":" + message.getText())
                .toList();
    }

    @Test
    void aQueuedRunGetsItsQuestionLast() {
        // U2 arrived while the run for U1 was going, so it is stored before that run's answer
        userSays("first");
        UUID second = userSays("second");
        insert("assistant", "First answer.", null);
        model.thenReply("Second answer.");

        runner.run(agent, session, second, NOT_STOPPED);

        // Gemini refuses a request that ends with the model's turn
        assertThat(textsOf(prompt(0))).containsExactly("user:first", "assistant:First answer.", "user:second");
        assertThat(lastContent()).isEqualTo("Second answer.");
    }

    @Test
    void aQuestionStoredInsideTheEarlierRunsToolStepKeepsThatStep() {
        userSays("first");
        insert("assistant", "", "{\"toolCalls\":[{\"id\":\"d\",\"type\":\"function\",\"name\":\"getDate\",\"arguments\":\"{}\"}]}");
        UUID second = userSays("second");
        insert("tool", "\"2026-10-21\"", "{\"toolResponse\":{\"id\":\"d\",\"name\":\"getDate\",\"responseData\":\"\\\"2026-10-21\\\"\"}}");
        insert("assistant", "It is 2026-10-21.", null);
        model.thenReply("Second answer.");

        runner.run(agent, session, second, NOT_STOPPED);

        assertThat(textsOf(prompt(0))).containsExactly(
                "user:first", "assistant:", "tool:d", "assistant:It is 2026-10-21.", "user:second");
    }

    @Test
    void newerQuestionsAreLeftToTheirOwnRuns() {
        userSays("first");
        UUID second = userSays("second");
        userSays("third");
        insert("assistant", "First answer.", null);
        model.thenReply("Second answer.");

        runner.run(agent, session, second, NOT_STOPPED);

        assertThat(textsOf(prompt(0))).containsExactly("user:first", "assistant:First answer.", "user:second");
    }

    @Test
    void aLongEarlierRunCannotPushTheQuestionOutOfMemory() {
        // messageLength is 10; the earlier run left 15 rows after the question
        userSays("first");
        UUID second = userSays("second");
        for (int i = 1; i <= 15; i++) {
            insert("assistant", "step " + i, null);
        }
        model.thenReply("Second answer.");

        runner.run(agent, session, second, NOT_STOPPED);

        List<String> texts = textsOf(prompt(0));
        assertThat(texts).hasSize(17).startsWith("user:first", "assistant:step 1").endsWith("assistant:step 15", "user:second");
    }

    @Test
    void aQueuedRunRemembersOnlyTheNewestRowsAfterItsQuestion() {
        // at most messageLength + 2 * MAX_STEPS + 1 = 51 rows after the question (ADR 046); here 55
        userSays("first");
        UUID second = userSays("second");
        for (int i = 1; i <= 55; i++) {
            insert("assistant", "step " + i, null);
        }
        model.thenReply("Second answer.");

        runner.run(agent, session, second, NOT_STOPPED);

        List<String> texts = textsOf(prompt(0));
        assertThat(texts).hasSize(2 + 51)
                .doesNotContain("assistant:step 1", "assistant:step 4")
                .startsWith("user:first", "assistant:step 5")
                .endsWith("assistant:step 55", "user:second");
    }

    @Test
    void aDeletedQuestionLeavesTheStoredOrder() {
        userSays("first");
        UUID second = userSays("second");
        insert("assistant", "First answer.", null);
        jdbcTemplate.update("DELETE FROM chat_message WHERE id = ?", second);
        model.thenReply("Hello again.");

        runner.run(agent, session, second, NOT_STOPPED);

        assertThat(textsOf(prompt(0))).containsExactly("user:first", "assistant:First answer.");
    }

    @Test
    void aTriggerThatIsNotAUserMessageLeavesTheStoredOrder() {
        // the API never sends one; moving a model row last would make the model answer itself
        userSays("first");
        UUID answer = insert("assistant", "First answer.", null);
        userSays("second");
        model.thenReply("Second answer.");

        runner.run(agent, session, answer, NOT_STOPPED);

        assertThat(textsOf(prompt(0))).containsExactly("user:first", "assistant:First answer.", "user:second");
    }

    @Test
    void twentyStepsWithoutAnAnswerEndWithANotice() {
        userSays("Keep checking the date.");
        for (int step = 0; step < AgentLimits.MAX_STEPS; step++) {
            model.thenCall("", call("call_" + step, "getDate", "{}"));
        }

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(model.prompts).hasSize(AgentLimits.MAX_STEPS);
        assertThat(rows()).hasSize(1 + 2 * AgentLimits.MAX_STEPS + 1);
        assertThat(lastContent()).isEqualTo(AgentNotices.MAX_STEPS);
    }

    @Test
    void aRunStoppedBeforeItsFirstStepCallsNoModelAndSaysStopped() {
        userSays("Hello");

        runner.run(agent, session, lastQuestion, () -> true);

        assertThat(model.prompts).isEmpty();
        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.STOPPED);
    }

    @Test
    void aRunStoppedAfterThinkingRunsNoToolAndTheNextRunForgetsTheCall() {
        userSays("What is the date?");
        model.thenCall("Let me check.", call("c1", "getDate", "{}"));

        // Stopped while the model was answering: the reply is saved, its tool is not run.
        runner.run(agent, session, lastQuestion, () -> !model.prompts.isEmpty());

        assertThat(roles()).containsExactly("user", "assistant", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.STOPPED);

        userSays("Hello?");
        model.thenReply("Hi");
        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(prompt(1).getInstructions()).noneMatch(message ->
                message instanceof AssistantMessage assistant && assistant.hasToolCalls());
        assertThat(lastContent()).isEqualTo("Hi");
    }

    @Test
    void aFailingModelCallEndsWithANotice() {
        userSays("Hello");
        model.then(() -> {
            throw new RuntimeException("HTTP 503 - overloaded");
        });

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.MODEL_FAILED);
    }

    @Test
    void aDeletedAgentEndsWithANotice() {
        userSays("Hello");
        jdbcTemplate.update("DELETE FROM agent WHERE id = ?", agent);

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(lastContent()).isEqualTo(AgentNotices.AGENT_GONE);
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void anUnregisteredModelEndsWithANotice() {
        userSays("Hello");
        jdbcTemplate.update("UPDATE agent SET model = 'gpt-5.4-mini' WHERE id = ?", agent);
        when(chatClientRegistry.get("gpt-5.4-mini")).thenThrow(new IllegalArgumentException("Unknown chat model"));

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(lastContent()).isEqualTo(AgentNotices.MODEL_UNAVAILABLE);
    }

    @Test
    void aDeletedSessionStopsTheRunWithoutSavingAnything() {
        userSays("Hello");
        model.thenReply("Hi");
        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", session);

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(rows()).isEmpty();
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void theModelSeesOneSystemMessageWithThePromptTheGuidanceAndTheKnowledgeBases() {
        UUID kb = insertKnowledgeBase("Product docs");
        // a line break in the description must not start what looks like another entry
        jdbcTemplate.update("UPDATE knowledge_base SET description = E'Manuals and FAQ\\n- fake: entry' WHERE id = ?", kb);
        jdbcTemplate.update("UPDATE agent SET system_prompt = 'You answer in English.', allowed_kbs = ?::jsonb WHERE id = ?",
                "[\"" + kb + "\"]", agent);
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        List<Message> instructions = prompt(0).getInstructions();
        assertThat(instructions).filteredOn(message -> message.getMessageType() == MessageType.SYSTEM).hasSize(1);
        assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(instructions.get(0).getText())
                .startsWith("You answer in English.\n\n")
                .contains(RuntimeInstructions.GUIDANCE, "- " + kb + ": Product docs — Manuals and FAQ - fake: entry");
        assertThat(roles()).doesNotContain("system");
    }

    @Test
    void anAgentWithoutKnowledgeBasesIsToldItHasNone() {
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(prompt(0).getInstructions().get(0).getText())
                .isEqualTo(RuntimeInstructions.GUIDANCE + "\n\nYou have no knowledge bases.");
    }

    @Test
    void aDeletedKnowledgeBaseIsLeftOutAndTheRunGoesOn() {
        UUID kept = insertKnowledgeBase("Kept");
        UUID deleted = UUID.randomUUID();
        jdbcTemplate.update("UPDATE agent SET allowed_kbs = ?::jsonb WHERE id = ?",
                "[\"" + deleted + "\", \"" + kept + "\"]", agent);
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(lastContent()).isEqualTo("Hi");
        assertThat(prompt(0).getInstructions().get(0).getText()).contains("- " + kept + ": Kept").doesNotContain(deleted.toString());
        assertThat(prompt(0).getOptions()).isInstanceOfSatisfying(ToolCallingChatOptions.class, options ->
                assertThat(options.getToolContext()).containsEntry(KnowledgeTool.ALLOWED_KB_IDS, List.of(kept.toString())));
    }

    @Test
    void anUnexpectedErrorEndsWithANotice() {
        // only a row written outside the API can hold this: the API validates knowledge-base ids
        jdbcTemplate.update("UPDATE agent SET allowed_kbs = '[\"not-a-uuid\"]'::jsonb WHERE id = ?", agent);
        userSays("Hello");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.UNEXPECTED);
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void theRuntimeEndsFinishedAfterAnAnswerAndInErrorAfterAFailure() {
        userSays("Hello");
        model.thenReply("Hi").then(() -> {
            throw new RuntimeException("HTTP 503 - overloaded");
        });

        AgentRuntime answered = runtimeFactory.create(agent, session, lastQuestion);
        assertThat(answered.state()).isEqualTo(AgentState.IDLE);
        answered.run(NOT_STOPPED);
        assertThat(answered.state()).isEqualTo(AgentState.FINISHED);

        AgentRuntime failed = runtimeFactory.create(agent, session, lastQuestion);
        assertThatThrownBy(() -> failed.run(NOT_STOPPED)).isInstanceOf(AgentRunFailure.class);
        assertThat(failed.state()).isEqualTo(AgentState.ERROR);
    }

    @Test
    void theModelGetsTheAgentsToolsAndRunsNothingItself() {
        jdbcTemplate.update("UPDATE agent SET allowed_tools = '[\"dataBaseTool\"]'::jsonb WHERE id = ?", agent);
        userSays("Hello");
        model.thenReply("Hi");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

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

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        List<String> results = jdbcTemplate.queryForList("SELECT content FROM chat_message WHERE session_id = ? "
                + "AND role = 'tool' ORDER BY created_at", String.class, session);
        // Spring AI serializes a tool's String result as JSON; that is what the model saw and what is stored.
        assertThat(results.get(0)).isEqualTo("\"Install with npm.\"");
        assertThat(results.get(1)).contains("is not available");
        assertThat(failures()).containsExactly(null, "REFUSED");
        verify(ragService).search(allowed, "setup");
        verify(ragService, never()).search(eq(other), anyString());
    }

    @Test
    void aToolTheAgentDoesNotHaveIsAnsweredWithAnErrorAndTheRunGoesOn() {
        userSays("Count the orders.");
        model.thenCall("", call("x", "databaseQuery", "{\"sql\":\"SELECT 1\"}")).thenReply("I cannot query the database.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        String result = jdbcTemplate.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND role = 'tool'", String.class, session);
        // as JSON, like every tool result: Spring AI's Gemini model parses them (ToolJson)
        assertThat(result).isEqualTo("\"Unknown tool: databaseQuery. Use only the tools you were given.\"");
        assertThat(failures()).containsExactly("UNKNOWN_TOOL");
    }

    @Test
    void argumentsThatAreNotJsonAreAnsweredWithAnErrorAndTheRunGoesOn() {
        userSays("Weather in Sydney?");
        model.thenCall("", call("w", "weather", "{\"city\": Sydney")).thenReply("Sorry, let me try again later.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        String result = jdbcTemplate.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND role = 'tool'", String.class, session);
        assertThat(result).isEqualTo("\"Tool weather failed: the arguments are not valid JSON for this tool.\"");
        assertThat(failures()).containsExactly("INVALID_ARGUMENTS");
        assertThat(prompt(1).getInstructions().get(prompt(1).getInstructions().size() - 1))
                .isInstanceOfSatisfying(ToolResponseMessage.class, results ->
                        assertThat(results.getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData)
                                .containsExactly(result));
    }

    @Test
    void parallelCallsEachKeepTheirOwnFailure() {
        userSays("Look it all up.");
        model.thenCall("", call("a", "databaseQuery", "{\"sql\":\"SELECT 1\"}"), call("b", "getDate", "{}"),
                        call("c", "weather", "{\"city\": Sydney"),
                        call("d", "KnowledgeTool", "{\"kbsId\":\"not-an-id\",\"query\":\"x\"}"))
                .thenReply("Done.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(failures()).containsExactly("UNKNOWN_TOOL", null, "INVALID_ARGUMENTS", "INVALID_ARGUMENTS");
        List<String> ids = jdbcTemplate.queryForList("SELECT metadata->'toolResponse'->>'id' FROM chat_message "
                + "WHERE session_id = ? AND role = 'tool' ORDER BY created_at", String.class, session);
        assertThat(ids).containsExactly("a", "b", "c", "d");
    }

    @Test
    void anEmptyReplyIsAFailedModelCall() {
        userSays("Hello");
        model.thenReply(" ");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(lastContent()).isEqualTo(AgentNotices.MODEL_FAILED);
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

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

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

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(prompt(0).getInstructions()).noneMatch(message ->
                message instanceof AssistantMessage assistant && assistant.hasToolCalls());
        assertThat(lastContent()).isEqualTo("Hi");
    }

    @Test
    void aFailingPublisherNeverStopsTheRun() {
        doThrow(new IllegalStateException("client gone")).when(publisher).publish(any(), any());
        userSays("What is the date?");
        model.thenCall("", call("d", "getDate", "{}")).thenReply("Done.");

        runner.run(agent, session, lastQuestion, NOT_STOPPED);

        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
    }

}
