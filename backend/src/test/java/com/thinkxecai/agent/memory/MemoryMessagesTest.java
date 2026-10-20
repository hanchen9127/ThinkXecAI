package com.thinkxecai.agent.memory;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Stored rows become the Spring AI messages the runtime produced, one tool-result message per step. */
class MemoryMessagesTest {

    private static ChatMessageDTO row(ChatMessageRole role, String text, ChatMessageMetadata metadata) {
        return ChatMessageDTO.builder().role(role).content(text).metadata(metadata).build();
    }

    private static ChatMessageDTO result(String id, String name, String data) {
        return row(ChatMessageRole.TOOL, data, new ChatMessageMetadata(null, new ToolResponseMetadata(id, name, data)));
    }

    @Test
    void eachRoleBecomesItsSpringAiMessage() {
        ChatMessageDTO call = row(ChatMessageRole.ASSISTANT, "Checking.", new ChatMessageMetadata(
                List.of(new ToolCallMetadata("call_1", "function", "weather", "{\"city\":\"Sydney\"}")), null));

        List<Message> messages = MemoryMessages.toSpringAi(List.of(
                row(ChatMessageRole.USER, "weather?", null), call, result("call_1", "weather", "\"sunny\""),
                row(ChatMessageRole.ASSISTANT, "It is sunny.", null)));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class).extracting(Message::getText).isEqualTo("weather?");
        assertThat(messages.get(1)).isInstanceOfSatisfying(AssistantMessage.class, assistant -> {
            assertThat(assistant.getText()).isEqualTo("Checking.");
            assertThat(assistant.getToolCalls()).containsExactly(
                    new AssistantMessage.ToolCall("call_1", "function", "weather", "{\"city\":\"Sydney\"}"));
        });
        assertThat(messages.get(2)).isInstanceOfSatisfying(ToolResponseMessage.class, tool ->
                assertThat(tool.getResponses()).containsExactly(
                        new ToolResponseMessage.ToolResponse("call_1", "weather", "\"sunny\"")));
        assertThat(messages.get(3)).isInstanceOfSatisfying(AssistantMessage.class, assistant ->
                assertThat(assistant.hasToolCalls()).isFalse());
    }

    @Test
    void theResultsOfParallelCallsBecomeOneMessage() {
        ChatMessageDTO calls = row(ChatMessageRole.ASSISTANT, "", new ChatMessageMetadata(List.of(
                new ToolCallMetadata("a", "function", "getCity", "{}"),
                new ToolCallMetadata("b", "function", "getDate", "{}")), null));

        List<Message> messages = MemoryMessages.toSpringAi(List.of(row(ChatMessageRole.USER, "q", null), calls,
                result("a", "getCity", "Sydney"), result("b", "getDate", "2026-10-19"),
                row(ChatMessageRole.USER, "next", null)));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(2)).isInstanceOfSatisfying(ToolResponseMessage.class, tool ->
                assertThat(tool.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("a", "b"));
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
    }

    @Test
    void storedSystemRowsAreNotPartOfTheMemory() {
        List<Message> messages = MemoryMessages.toSpringAi(List.of(
                row(ChatMessageRole.SYSTEM, "prompt", null), row(ChatMessageRole.USER, "q", null)));

        assertThat(messages).singleElement().isInstanceOf(UserMessage.class);
    }

    @Test
    void storedToolDataThatIsNotJsonIsSentAsJson() {
        // Spring AI's Gemini model parses every result and every call's arguments as JSON (ToolJson)
        ChatMessageDTO calls = row(ChatMessageRole.ASSISTANT, "", new ChatMessageMetadata(List.of(
                new ToolCallMetadata("a", "function", "weather", ""),
                new ToolCallMetadata("b", "function", "weather", "{\"city\": Sydney"),
                new ToolCallMetadata("c", "function", "weather", "{\"city\":\"Sydney\"}")), null));

        List<Message> messages = MemoryMessages.toSpringAi(List.of(row(ChatMessageRole.USER, "q", null), calls,
                result("a", "weather", "Unknown tool: weather."), result("b", "weather", ""),
                result("c", "weather", "{\"sky\":\"sunny\"}")));

        assertThat(messages.get(1)).isInstanceOfSatisfying(AssistantMessage.class, assistant ->
                assertThat(assistant.getToolCalls()).extracting(AssistantMessage.ToolCall::arguments)
                        .containsExactly("{}", "{}", "{\"city\":\"Sydney\"}"));
        assertThat(messages.get(2)).isInstanceOfSatisfying(ToolResponseMessage.class, tool ->
                assertThat(tool.getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData)
                        .containsExactly("\"Unknown tool: weather.\"", "\"\"", "{\"sky\":\"sunny\"}"));
    }

    @Test
    void anAssistantMessageWithoutTextHasEmptyText() {
        List<Message> messages = MemoryMessages.toSpringAi(List.of(row(ChatMessageRole.ASSISTANT, null, null)));

        assertThat(messages.get(0).getText()).isEmpty();
    }

}
