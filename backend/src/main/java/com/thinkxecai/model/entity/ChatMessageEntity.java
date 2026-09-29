package com.thinkxecai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** role mirrors the CHECK constraint's raw text value; metadata is raw JSONB text. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity {

    private UUID id;
    private UUID sessionId;
    private String role;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
