package com.thinkxecai.agent;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolFailureKind;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what a run generates into stored messages, the reverse of {@code MemoryMessages}: an assistant
 * message with its tool calls, one {@code tool} row per tool result, or a notice. Gemini's thought
 * signatures are not stored (they matter only within the run, which keeps the original messages).
 */
public final class GeneratedMessages {

    private GeneratedMessages() {
    }

    /** @throws IllegalStateException if a tool call has no id: {@link ToolCallIds#fill} runs first */
    public static ChatMessageDTO assistant(AssistantMessage message) {
        List<ToolCallMetadata> calls = message.getToolCalls().stream()
                .map(call -> {
                    if (call.id() == null || call.id().isBlank()) {
                        throw new IllegalStateException("Tool call " + call.name() + " has no id");
                    }
                    return new ToolCallMetadata(call.id(), call.type(), call.name(), call.arguments());
                })
                .toList();
        return ChatMessageDTO.builder()
                .role(ChatMessageRole.ASSISTANT)
                .content(message.getText() == null ? "" : message.getText())
                .metadata(calls.isEmpty() ? null : new ChatMessageMetadata(calls, null))
                .build();
    }

    /**
     * One {@code tool} row per result, each with why it failed, if it did (ADR 064).
     *
     * @param failures one per result, in the same order; null where the call succeeded
     * @throws IllegalArgumentException if there is not one entry per result
     */
    public static List<ChatMessageDTO> toolResults(ToolResponseMessage message, List<ToolFailureKind> failures) {
        List<ToolResponseMessage.ToolResponse> responses = message.getResponses();
        if (failures.size() != responses.size()) {
            throw new IllegalArgumentException(failures.size() + " failures for " + responses.size() + " tool results");
        }
        List<ChatMessageDTO> rows = new ArrayList<>(responses.size());
        for (int i = 0; i < responses.size(); i++) {
            ToolResponseMessage.ToolResponse response = responses.get(i);
            rows.add(ChatMessageDTO.builder()
                    .role(ChatMessageRole.TOOL)
                    .content(response.responseData())
                    .metadata(new ChatMessageMetadata(null, new ToolResponseMetadata(
                            response.id(), response.name(), response.responseData(), failures.get(i))))
                    .build());
        }
        return rows;
    }

    /** A plain assistant message telling the user why a run stopped (ADR 043). */
    public static ChatMessageDTO notice(String text) {
        return ChatMessageDTO.builder().role(ChatMessageRole.ASSISTANT).content(text).build();
    }

}
