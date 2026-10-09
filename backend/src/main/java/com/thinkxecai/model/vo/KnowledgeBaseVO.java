package com.thinkxecai.model.vo;

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
public class KnowledgeBaseVO {

    private String id;
    private String name;
    @Nullable
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
