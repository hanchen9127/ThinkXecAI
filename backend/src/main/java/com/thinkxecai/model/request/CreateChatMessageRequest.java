package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import lombok.Data;


@Data
public class CreateChatMessageRequest {

    private String sessionId;
    private ChatMessageRole role;
    private String content;
    private ChatMessageMetadata metadata;

}
