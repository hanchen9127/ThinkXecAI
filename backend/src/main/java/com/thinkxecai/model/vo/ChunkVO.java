package com.thinkxecai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** View shape for retrieval results; the embedding vector is never exposed downstream. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkVO {

    private String id;
    private String kbId;
    private String docId;
    private String title;
    private String content;
    private LocalDateTime createdAt;

}
