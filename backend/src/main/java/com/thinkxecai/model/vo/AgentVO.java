package com.thinkxecai.model.vo;

import com.thinkxecai.model.ChatModelNames;
import com.thinkxecai.model.ChatOptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
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
    @Nullable
    private String description;
    @Nullable
    private String systemPrompt;
    @Schema(allowableValues = {ChatModelNames.DEEPSEEK_CHAT, ChatModelNames.GEMINI_FLASH, ChatModelNames.QWEN_FLASH})
    private String model;
    private List<String> allowedTools;
    private List<String> allowedKbs;
    private ChatOptions chatOptions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
