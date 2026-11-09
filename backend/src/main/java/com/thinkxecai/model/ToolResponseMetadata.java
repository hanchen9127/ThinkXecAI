package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

/**
 * Mirrors Spring AI's ToolResponseMessage.ToolResponse; {@code id} matches the answered ToolCallMetadata id.
 * {@code failure} is null when the call did what was asked (ADR 064); tool messages saved before it have
 * none, which reads as null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolResponseMetadata(String id, String name, String responseData, @Nullable ToolFailureKind failure) {
}
