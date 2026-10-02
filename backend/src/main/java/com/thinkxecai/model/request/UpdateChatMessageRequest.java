package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatMessageMetadata;
import lombok.Data;


@Data
public class UpdateChatMessageRequest {

    private String content;
    private ChatMessageMetadata metadata;

}
