package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors Spring AI's ToolResponseMessage.ToolResponse; {@code id} matches the answered ToolCallMetadata id. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolResponseMetadata(String id, String name, String responseData) {
}
