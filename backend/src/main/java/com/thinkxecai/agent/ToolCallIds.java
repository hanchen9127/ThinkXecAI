package com.thinkxecai.agent;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.UUID;

/**
 * Gives every tool call an id before its tools run. A model may return calls without one (Gemini did
 * before 3.x), but DeepSeek requires every tool result to name the call it answers, so a session
 * switched between models needs stored ids. The filled message replaces the original in the run's
 * memory: {@code ToolCallingManager} copies each call's id into its result, so call and result match.
 */
public final class ToolCallIds {

    private ToolCallIds() {
    }

    /** The message itself when every call has an id; otherwise a copy with {@code call_<uuid>} ids. */
    public static AssistantMessage fill(AssistantMessage message) {
        List<AssistantMessage.ToolCall> calls = message.getToolCalls();
        if (calls.stream().allMatch(call -> call.id() != null && !call.id().isBlank())) {
            return message;
        }
        List<AssistantMessage.ToolCall> filled = calls.stream()
                .map(call -> call.id() != null && !call.id().isBlank() ? call
                        : new AssistantMessage.ToolCall("call_" + UUID.randomUUID(), call.type(), call.name(),
                                call.arguments()))
                .toList();
        // Text and metadata are kept: Gemini's thought signatures must go back with this message.
        return AssistantMessage.builder()
                .content(message.getText())
                .properties(message.getMetadata())
                .toolCalls(filled)
                .media(message.getMedia())
                .build();
    }

}
