package com.thinkxecai.converter;

import com.thinkxecai.model.dto.ChatSessionDTO;
import com.thinkxecai.model.entity.ChatSessionEntity;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ChatSessionConverter {

    public ChatSessionDTO toDTO(ChatSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatSessionDTO.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ChatSessionEntity toEntity(ChatSessionDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatSessionEntity.builder()
                .id(dto.getId())
                .agentId(dto.getAgentId())
                .title(dto.getTitle())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ChatSessionVO toVO(ChatSessionDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatSessionVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .agentId(dto.getAgentId() != null ? dto.getAgentId().toString() : null)
                .title(dto.getTitle())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ChatSessionDTO fromCreateRequest(CreateChatSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return ChatSessionDTO.builder()
                .agentId(request.getAgentId() != null ? UUID.fromString(request.getAgentId()) : null)
                .title(request.getTitle())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** Merges non-null fields from an UpdateChatSessionRequest onto the existing DTO (PATCH semantics). */
    public ChatSessionDTO applyUpdateRequest(ChatSessionDTO existing, UpdateChatSessionRequest request) {
        if (request.getAgentId() != null) existing.setAgentId(UUID.fromString(request.getAgentId()));
        if (request.getTitle() != null) existing.setTitle(request.getTitle());
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }

}
