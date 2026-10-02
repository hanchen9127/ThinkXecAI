package com.thinkxecai.converter;

import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import com.thinkxecai.model.entity.KnowledgeBaseEntity;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;
import org.springframework.stereotype.Component;


@Component
public class KnowledgeBaseConverter {

    public KnowledgeBaseDTO toDTO(KnowledgeBaseEntity entity) {
        if (entity == null) {
            return null;
        }
        return KnowledgeBaseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public KnowledgeBaseEntity toEntity(KnowledgeBaseDTO dto) {
        if (dto == null) {
            return null;
        }
        return KnowledgeBaseEntity.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public KnowledgeBaseVO toVO(KnowledgeBaseDTO dto) {
        if (dto == null) {
            return null;
        }
        return KnowledgeBaseVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .name(dto.getName())
                .description(dto.getDescription())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public KnowledgeBaseDTO fromCreateRequest(CreateKnowledgeBaseRequest request) {
        return KnowledgeBaseDTO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }

    /** Merges non-null fields from an UpdateKnowledgeBaseRequest onto the existing DTO (PATCH semantics). */
    public KnowledgeBaseDTO applyUpdateRequest(KnowledgeBaseDTO existing, UpdateKnowledgeBaseRequest request) {
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        return existing;
    }

}
