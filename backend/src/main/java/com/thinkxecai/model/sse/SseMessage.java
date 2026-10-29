package com.thinkxecai.model.sse;

import com.thinkxecai.model.vo.ChatMessageVO;
import jakarta.annotation.Nullable;

/**
 * One event on a chat session's stream, as the frontend's hand-written {@code SseMessageEvent} reads it
 * (ADR 021: /sse is not in the OpenAPI document). Written by Spring MVC's JSON converter, so a pushed
 * message is the same JSON as the REST history returns. The starting design serialized it itself.
 */
public record SseMessage(Type type, Payload payload, Metadata metadata) {

    /** The runtime emits only {@code AI_GENERATED_CONTENT}; the others are kept for the frontend's type (v2). */
    public enum Type {
        AI_GENERATED_CONTENT,
        AI_PLANNING,
        AI_THINKING,
        AI_EXECUTING,
        AI_DONE
    }

    public record Payload(@Nullable ChatMessageVO message, @Nullable String statusText, boolean done) {
    }

    public record Metadata(@Nullable String chatMessageId) {
    }

    /** A message an agent run has saved. */
    public static SseMessage generated(ChatMessageVO message) {
        return new SseMessage(Type.AI_GENERATED_CONTENT, new Payload(message, null, false),
                new Metadata(message.getId()));
    }

}
