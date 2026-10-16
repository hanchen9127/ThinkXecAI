package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Mirrors the chat_message.role CHECK constraint: user, assistant, system, tool. */
public enum ChatMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ChatMessageRole fromJson(String value) {
        return ChatMessageRole.valueOf(value.toUpperCase(Locale.ROOT));
    }

}
