package com.thinkxecai.model.dto;

import com.thinkxecai.model.ChatOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDTO {

    private UUID id;
    private String name;
    private String description;
    private String systemPrompt;
    private String model;
    private List<String> allowedTools;
    private List<String> allowedKbs;
    private ChatOptions chatOptions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
