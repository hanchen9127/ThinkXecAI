package com.thinkxecai.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateChatSessionRequest {

    /** Optional; when given it must refer to an existing agent (checked by the service). Fixed after creation. */
    private UUID agentId;
    @NullOrNotBlank
    @Size(max = RequestLimits.NAME)
    private String title;

}
