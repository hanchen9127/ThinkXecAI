package com.thinkxecai.converter;

import com.thinkxecai.model.dto.ChunkDTO;
import com.thinkxecai.model.entity.ChunkEntity;
import com.thinkxecai.model.request.CreateChunkRequest;
import com.thinkxecai.model.vo.ChunkVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ChunkConverter {

    public ChunkDTO toDTO(ChunkEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChunkDTO.builder()
                .id(entity.getId())
                .kbId(entity.getKbId())
                .docId(entity.getDocId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .embedding(entity.getEmbedding())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public ChunkEntity toEntity(ChunkDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChunkEntity.builder()
                .id(dto.getId())
                .kbId(dto.getKbId())
                .docId(dto.getDocId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .embedding(dto.getEmbedding())
                .createdAt(dto.getCreatedAt())
                .build();
    }

    public ChunkVO toVO(ChunkDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChunkVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .kbId(dto.getKbId() != null ? dto.getKbId().toString() : null)
                .docId(dto.getDocId() != null ? dto.getDocId().toString() : null)
                .title(dto.getTitle())
                .content(dto.getContent())
                .createdAt(dto.getCreatedAt())
                .build();
    }

    /** Embedding is left unset here; the ingestion service fills it in after calling Ollama. */
    public ChunkDTO fromCreateRequest(CreateChunkRequest request) {
        return ChunkDTO.builder()
                .kbId(UUID.fromString(request.getKbId()))
                .docId(UUID.fromString(request.getDocId()))
                .title(request.getTitle())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
    }

}
