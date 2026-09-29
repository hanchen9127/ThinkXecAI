package com.thinkxecai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionEntity {

    private UUID id;
    private UUID agentId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
