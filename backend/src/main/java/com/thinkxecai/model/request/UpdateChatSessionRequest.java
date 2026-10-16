package com.thinkxecai.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Only the title can change. The agent is fixed at creation: the session's history (tool calls and
 * their results) only makes sense with that agent's tools and knowledge bases.
 */
@Data
public class UpdateChatSessionRequest {

    @NullOrNotBlank
    @Size(max = RequestLimits.NAME)
    private String title;

}
