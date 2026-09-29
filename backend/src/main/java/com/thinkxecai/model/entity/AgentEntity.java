package com.thinkxecai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirrors the agent table row-for-row. JSONB columns (allowed_tools, allowed_kbs,
 * chat_options) are kept as raw JSON text here; converters parse them into typed DTO fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentEntity {

    private UUID id;
    private String name;
    private String description;
    private String systemPrompt;
    private String model;
    private String allowedTools;
    private String allowedKbs;
    private String chatOptions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
