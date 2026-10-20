package com.thinkxecai.agent.memory;

import com.thinkxecai.agent.ToolJson;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns sanitized stored messages into Spring AI messages for the agent's memory. The results of one
 * assistant message's tool calls are stored as one row each and become one {@link ToolResponseMessage}
 * again, as {@code ToolCallingManager} produced them: Gemini expects the answers to parallel calls in
 * one turn. Stored {@code system} rows are skipped (the runtime never writes them; the system message
 * is built for each run). Tool results and call arguments go through {@link ToolJson}, so a stored
 * plain text or a malformed argument string cannot break a Gemini request.
 */
public final class MemoryMessages {

    private MemoryMessages() {
    }

    /** @param messages the output of {@link MemorySanitizer#sanitize} */
    public static List<Message> toSpringAi(List<ChatMessageDTO> messages) {
        List<Message> converted = new ArrayList<>();
        List<ToolResponseMessage.ToolResponse> pendingResults = new ArrayList<>();
        for (ChatMessageDTO message : messages) {
            if (message.getRole() != ChatMessageRole.TOOL && !pendingResults.isEmpty()) {
                converted.add(toolResults(pendingResults));
                pendingResults = new ArrayList<>();
            }
            switch (message.getRole()) {
                case USER -> converted.add(new UserMessage(text(message)));
                case ASSISTANT -> converted.add(AssistantMessage.builder()
                        .content(text(message))
                        .toolCalls(toolCalls(message))
                        .build());
                case TOOL -> pendingResults.add(toolResult(message));
                case SYSTEM -> {
                }
            }
        }
        if (!pendingResults.isEmpty()) {
            converted.add(toolResults(pendingResults));
        }
        return converted;
    }

    private static String text(ChatMessageDTO message) {
        return message.getContent() == null ? "" : message.getContent();
    }

    private static List<AssistantMessage.ToolCall> toolCalls(ChatMessageDTO message) {
        if (message.getMetadata() == null || message.getMetadata().toolCalls() == null) {
            return List.of();
        }
        return message.getMetadata().toolCalls().stream()
                .map(MemoryMessages::toolCall)
                .toList();
    }

    private static AssistantMessage.ToolCall toolCall(ToolCallMetadata call) {
        return new AssistantMessage.ToolCall(call.id(), call.type() == null ? "function" : call.type(),
                call.name(), ToolJson.arguments(call.arguments()));
    }

    private static ToolResponseMessage.ToolResponse toolResult(ChatMessageDTO message) {
        ToolResponseMetadata response = message.getMetadata().toolResponse();
        String data = response.responseData() != null ? response.responseData() : text(message);
        return new ToolResponseMessage.ToolResponse(response.id(), response.name(), ToolJson.result(data));
    }

    private static ToolResponseMessage toolResults(List<ToolResponseMessage.ToolResponse> results) {
        return ToolResponseMessage.builder().responses(results).build();
    }

}
