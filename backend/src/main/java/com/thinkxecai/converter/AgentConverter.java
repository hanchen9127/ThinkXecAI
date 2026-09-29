package com.thinkxecai.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatOptions;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.entity.AgentEntity;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Converts the agent domain between its persisted JSONB-as-text shape (AgentEntity) and its
 * typed in-memory shape (AgentDTO/AgentVO/request objects).
 */
@Component
@RequiredArgsConstructor
public class AgentConverter {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AgentDTO toDTO(AgentEntity entity) {
        if (entity == null) {
            return null;
        }
        return AgentDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .systemPrompt(entity.getSystemPrompt())
                .model(entity.getModel())
                .allowedTools(readList(entity.getAllowedTools()))
                .allowedKbs(readList(entity.getAllowedKbs()))
                .chatOptions(readChatOptions(entity.getChatOptions()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public AgentEntity toEntity(AgentDTO dto) {
        if (dto == null) {
            return null;
        }
        return AgentEntity.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .systemPrompt(dto.getSystemPrompt())
                .model(dto.getModel())
                .allowedTools(writeJson(dto.getAllowedTools()))
                .allowedKbs(writeJson(dto.getAllowedKbs()))
                .chatOptions(writeJson(dto.getChatOptions()))
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public AgentVO toVO(AgentDTO dto) {
        if (dto == null) {
            return null;
        }
        return AgentVO.builder()
                .id(dto.getId() != null ? dto.getId().toString() : null)
                .name(dto.getName())
                .description(dto.getDescription())
                .systemPrompt(dto.getSystemPrompt())
                .model(dto.getModel())
                .allowedTools(dto.getAllowedTools())
                .allowedKbs(dto.getAllowedKbs())
                .chatOptions(dto.getChatOptions())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public AgentDTO fromCreateRequest(CreateAgentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return AgentDTO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemPrompt(request.getSystemPrompt())
                .model(request.getModel())
                .allowedTools(request.getAllowedTools() != null ? request.getAllowedTools() : List.of())
                .allowedKbs(request.getAllowedKbs() != null ? request.getAllowedKbs() : List.of())
                .chatOptions(request.getChatOptions() != null ? request.getChatOptions() : new ChatOptions())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** Merges non-null fields from an UpdateAgentRequest onto the existing DTO (PATCH semantics). */
    public AgentDTO applyUpdateRequest(AgentDTO existing, UpdateAgentRequest request) {
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getSystemPrompt() != null) existing.setSystemPrompt(request.getSystemPrompt());
        if (request.getModel() != null) existing.setModel(request.getModel());
        if (request.getAllowedTools() != null) existing.setAllowedTools(request.getAllowedTools());
        if (request.getAllowedKbs() != null) existing.setAllowedKbs(request.getAllowedKbs());
        if (request.getChatOptions() != null) existing.setChatOptions(request.getChatOptions());
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to parse agent JSON field: " + e.getOriginalMessage());
        }
    }

    private ChatOptions readChatOptions(String json) {
        if (json == null || json.isBlank()) {
            return new ChatOptions();
        }
        try {
            return objectMapper.readValue(json, ChatOptions.class);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to parse agent chatOptions: " + e.getOriginalMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to serialize agent JSON field: " + e.getOriginalMessage());
        }
    }

}
