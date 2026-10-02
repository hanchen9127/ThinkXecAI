package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors Spring AI's AssistantMessage.ToolCall; {@code arguments} is the raw JSON argument string. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallMetadata(String id, String type, String name, String arguments) {
}
