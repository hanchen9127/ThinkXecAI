package com.thinkxecai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** metadata holds the on-disk relative file path plus any other file details, as raw JSONB text. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    private UUID id;
    private UUID kbId;
    private String name;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
