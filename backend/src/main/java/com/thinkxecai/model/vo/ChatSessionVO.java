package com.thinkxecai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionVO {

    private String id;
    private String agentId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
