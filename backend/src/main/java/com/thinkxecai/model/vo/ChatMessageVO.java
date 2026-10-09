package com.thinkxecai.model.vo;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageVO {

    private String id;
    private String sessionId;
    private ChatMessageRole role;
    @Nullable
    private String content;
    @Nullable
    private ChatMessageMetadata metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
