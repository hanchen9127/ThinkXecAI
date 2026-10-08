package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * There is no agentId: the agent is resolved from the session. A user message must have non-blank
 * content (checked by the service, since the rule depends on the role).
 */
@Data
public class CreateChatMessageRequest {

    @NotNull
    private UUID sessionId;
    @NotNull
    private ChatMessageRole role;
    private String content;
    private ChatMessageMetadata metadata;

}
