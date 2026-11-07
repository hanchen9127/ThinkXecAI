package com.thinkxecai.agent.memory;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window cut at any row, or left behind by a run that died, becomes a history both models accept:
 * it starts with a user message, and every kept tool call has its result directly after its
 * assistant message (ISSUE-003 B).
 */
class MemorySanitizerTest {

    private static ChatMessageDTO user(String text) {
        return message(ChatMessageRole.USER, text, null);
    }

    private static ChatMessageDTO assistant(String text) {
        return message(ChatMessageRole.ASSISTANT, text, null);
    }

    private static ChatMessageDTO calling(String text, String... callIds) {
        List<ToolCallMetadata> calls = Arrays.stream(callIds)
                .map(id -> new ToolCallMetadata(id, "function", "tool-" + id, "{}"))
                .toList();
        return message(ChatMessageRole.ASSISTANT, text, new ChatMessageMetadata(calls, null));
    }

    private static ChatMessageDTO result(String callId) {
        return message(ChatMessageRole.TOOL, "result " + callId,
                new ChatMessageMetadata(null, new ToolResponseMetadata(callId, "tool-" + callId, "result " + callId, null)));
    }

    private static ChatMessageDTO message(ChatMessageRole role, String text, ChatMessageMetadata metadata) {
        return ChatMessageDTO.builder().role(role).content(text).metadata(metadata).build();
    }

    private static List<String> describe(List<ChatMessageDTO> messages) {
        return messages.stream().map(MemorySanitizerTest::describe).toList();
    }

    private static String describe(ChatMessageDTO message) {
        String calls = message.getMetadata() == null || message.getMetadata().toolCalls() == null ? ""
                : message.getMetadata().toolCalls().stream().map(ToolCallMetadata::id).toList().toString();
        return message.getRole() + ":" + message.getContent() + calls;
    }

    @Test
    void aCleanHistoryIsUnchanged() {
        List<ChatMessageDTO> history = List.of(user("weather?"), calling("", "a", "b"), result("a"), result("b"),
                assistant("sunny"), user("thanks"), assistant("welcome"));

        assertThat(MemorySanitizer.sanitize(history)).containsExactlyElementsOf(history);
    }

    @Test
    void aWindowThatStartsOnAToolResultStartsAtTheNextUserMessage() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                result("a"), assistant("sunny"), user("and tomorrow?"), assistant("rain")));

        assertThat(describe(sanitized)).containsExactly("USER:and tomorrow?", "ASSISTANT:rain");
    }

    @Test
    void aWindowThatStartsOnAnAssistantCallSkipsTheWholeStep() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                calling("", "a"), result("a"), assistant("sunny"), user("thanks")));

        assertThat(describe(sanitized)).containsExactly("USER:thanks");
    }

    @Test
    void aCallWithoutResultFromADeadRunIsDroppedAndItsTextKept() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("weather?"), calling("Let me check.", "a"), user("hello?"), assistant("hi")));

        assertThat(describe(sanitized))
                .containsExactly("USER:weather?", "ASSISTANT:Let me check.", "USER:hello?", "ASSISTANT:hi");
        assertThat(sanitized.get(1).getMetadata()).isNull();
    }

    @Test
    void aCallWithoutResultAndWithoutTextIsDroppedEntirely() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(user("weather?"), calling("", "a")));

        assertThat(describe(sanitized)).containsExactly("USER:weather?");
    }

    @Test
    void parallelCallsKeepOnlyTheAnsweredOnes() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("weather in two cities?"), calling("", "a", "b"), result("b"), assistant("done")));

        assertThat(describe(sanitized)).containsExactly(
                "USER:weather in two cities?", "ASSISTANT:[b]", "TOOL:result b", "ASSISTANT:done");
    }

    @Test
    void aResultSeparatedFromItsCallIsDropped() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("q1"), calling("", "a"), user("q2"), result("a"), assistant("answer")));

        assertThat(describe(sanitized)).containsExactly("USER:q1", "USER:q2", "ASSISTANT:answer");
    }

    @Test
    void aResultForAnotherCallOrAnAnsweredCallIsDropped() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("q"), calling("", "a"), result("x"), result("a"), result("a"), assistant("answer")));

        assertThat(describe(sanitized)).containsExactly("USER:q", "ASSISTANT:[a]", "TOOL:result a", "ASSISTANT:answer");
    }

    @Test
    void storedSystemRowsAreSkipped() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                message(ChatMessageRole.SYSTEM, "old prompt", null), user("q"), assistant("a")));

        assertThat(describe(sanitized)).containsExactly("USER:q", "ASSISTANT:a");
    }

    @Test
    void anAssistantMessageWithoutCallsAndWithoutTextIsDropped() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("q"), assistant(""), user("again"), assistant(" "), assistant(null), assistant("answer")));

        assertThat(describe(sanitized)).containsExactly("USER:q", "USER:again", "ASSISTANT:answer");
    }

    @Test
    void aCallWithABlankIdCannotBeAnswered() {
        List<ChatMessageDTO> sanitized = MemorySanitizer.sanitize(List.of(
                user("q"), calling("text", " "), result(" "), assistant("answer")));

        assertThat(describe(sanitized)).containsExactly("USER:q", "ASSISTANT:text", "ASSISTANT:answer");
    }

}
