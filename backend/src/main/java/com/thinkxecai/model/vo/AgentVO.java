package com.thinkxecai.model.vo;

import com.thinkxecai.model.ChatOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentVO {

    private String id;
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
