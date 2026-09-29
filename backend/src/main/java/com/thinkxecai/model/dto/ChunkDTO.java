package com.thinkxecai.model.dto;

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
public class ChunkDTO {

    private UUID id;
    private UUID kbId;
    private UUID docId;
    private String title;
    private String content;
    private float[] embedding;
    private LocalDateTime createdAt;

}
