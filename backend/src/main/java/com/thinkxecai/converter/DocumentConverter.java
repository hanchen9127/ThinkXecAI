package com.thinkxecai.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.dto.DocumentDTO;
import com.thinkxecai.model.entity.DocumentEntity;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentConverter {

    private static final TypeReference<Map<String, Object>> METADATA_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DocumentDTO toDTO(DocumentEntity entity) {
        if (entity == null) {
            return null;
        }
        return DocumentDTO.builder()
                .id(entity.getId())
                .kbId(entity.getKbId())
                .name(entity.getName())
                .metadata(readMetadata(entity.getMetadata()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public DocumentEntity toEntity(DocumentDTO dto) {
        if (dto == null) {
            return null;
        }
        return DocumentEntity.builder()
                .id(dto.getId())
                .kbId(dto.getKbId())
                .name(dto.getName())
                .metadata(writeMetadata(dto.getMetadata()))
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public DocumentVO toVO(DocumentDTO dto) {
        if (dto == null) {
            return null;
        }
        return DocumentVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .kbId(dto.getKbId() != null ? dto.getKbId().toString() : null)
                .name(dto.getName())
                .metadata(dto.getMetadata())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public DocumentDTO fromCreateRequest(CreateDocumentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return DocumentDTO.builder()
                .kbId(UUID.fromString(request.getKbId()))
                .name(request.getName())
                .metadata(request.getMetadata())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** Merges non-null fields from an UpdateDocumentRequest onto the existing DTO (PATCH semantics). */
    public DocumentDTO applyUpdateRequest(DocumentDTO existing, UpdateDocumentRequest request) {
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getMetadata() != null) existing.setMetadata(request.getMetadata());
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, METADATA_MAP);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to parse document metadata: " + e.getOriginalMessage());
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to serialize document metadata: " + e.getOriginalMessage());
        }
    }

}
