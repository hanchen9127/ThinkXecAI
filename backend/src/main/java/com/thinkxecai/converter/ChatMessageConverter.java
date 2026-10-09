package com.thinkxecai.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.entity.ChatMessageEntity;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.request.UpdateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class ChatMessageConverter {

    private final ObjectMapper objectMapper;

    public ChatMessageDTO toDTO(ChatMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatMessageDTO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(ChatMessageRole.valueOf(entity.getRole().toUpperCase()))
                .content(entity.getContent())
                .metadata(readMetadata(entity.getMetadata()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ChatMessageEntity toEntity(ChatMessageDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatMessageEntity.builder()
                .id(dto.getId())
                .sessionId(dto.getSessionId())
                .role(dto.getRole() != null ? dto.getRole().toJson() : null)
                .content(dto.getContent())
                .metadata(writeMetadata(dto.getMetadata()))
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ChatMessageVO toVO(ChatMessageDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatMessageVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .sessionId(dto.getSessionId() != null ? dto.getSessionId().toString() : null)
                .role(dto.getRole())
                .content(dto.getContent())
                .metadata(dto.getMetadata())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ChatMessageDTO fromCreateRequest(CreateChatMessageRequest request) {
        return ChatMessageDTO.builder()
                .sessionId(request.getSessionId())
                .role(request.getRole())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .build();
    }

    /** Merges non-null fields from an UpdateChatMessageRequest onto the existing DTO (PATCH semantics). */
    public ChatMessageDTO applyUpdateRequest(ChatMessageDTO existing, UpdateChatMessageRequest request) {
        if (request.getContent() != null) existing.setContent(request.getContent());
        if (request.getMetadata() != null) existing.setMetadata(request.getMetadata());
        return existing;
    }

    private ChatMessageMetadata readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChatMessageMetadata.class);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to parse chat message metadata: " + e.getOriginalMessage());
        }
    }

    private String writeMetadata(ChatMessageMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to serialize chat message metadata: " + e.getOriginalMessage());
        }
    }

}
