package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatMessageRole;
import lombok.Data;

import java.util.Map;

@Data
public class CreateChatMessageRequest {

    private String sessionId;
    private ChatMessageRole role;
    private String content;
    private Map<String, Object> metadata;

}
