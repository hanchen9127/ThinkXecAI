package com.thinkxecai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One embedded chunk_bge_m3 row: a parsed Markdown section plus its bge-m3 embedding. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkEntity {

    private UUID id;
    private UUID kbId;
    private UUID docId;
    private String title;
    private String content;
    private float[] embedding;
    private LocalDateTime createdAt;

}
