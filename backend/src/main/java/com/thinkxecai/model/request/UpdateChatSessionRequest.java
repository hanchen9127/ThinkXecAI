package com.thinkxecai.model.request;

import lombok.Data;

/**
 * Only the title can change. The agent is fixed at creation: the session's history (tool calls and
 * their results) only makes sense with that agent's tools and knowledge bases.
 */
@Data
public class UpdateChatSessionRequest {

    @NullOrNotBlank
    private String title;

}
