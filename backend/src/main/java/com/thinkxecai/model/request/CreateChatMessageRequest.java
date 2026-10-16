package com.thinkxecai.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Always a user message (ADR 031): there is no role, metadata, or agentId. Assistant, tool, and system
 * messages are written only by the agent runtime; the agent is resolved from the session.
 */
@Data
public class CreateChatMessageRequest {

    @NotNull
    private UUID sessionId;
    @NotBlank
    private String content;

}
