package com.thinkxecai.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateChatMessageRequest {

    private String content;
    private Map<String, Object> metadata;

}
