package com.thinkxecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * chat_message.metadata. Assistant messages that request tools carry {@code toolCalls}; tool
 * messages carry the single {@code toolResponse} they answer; other messages have no metadata.
 * Own types rather than Spring AI's so the stored JSON does not move with Spring AI versions;
 * the agent runtime maps between them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessageMetadata(List<ToolCallMetadata> toolCalls, ToolResponseMetadata toolResponse) {
}
