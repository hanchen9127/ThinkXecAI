package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mirrors the chat_message.role CHECK constraint: user, assistant, system, tool. */
public enum ChatMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ChatMessageRole fromJson(String value) {
        return ChatMessageRole.valueOf(value.toUpperCase());
    }

}
