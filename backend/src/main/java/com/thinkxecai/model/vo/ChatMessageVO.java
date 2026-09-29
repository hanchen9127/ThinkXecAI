package com.thinkxecai.model.vo;

import com.thinkxecai.model.ChatMessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageVO {

    private String id;
    private String sessionId;
    private ChatMessageRole role;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
