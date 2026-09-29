package com.thinkxecai.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.entity.ChatMessageEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageConverterTest {

    private final ChatMessageConverter converter = new ChatMessageConverter(new ObjectMapper());

    @Test
    void metadataRoundTripsBetweenDtoAndEntity() {
        ChatMessageDTO dto = ChatMessageDTO.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .role(ChatMessageRole.TOOL)
                .content("tool result")
                .metadata(Map.of("toolCallId", "call-1", "toolName", "dateTool"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatMessageEntity entity = converter.toEntity(dto);
        assertThat(entity.getMetadata()).contains("toolCallId").contains("call-1");
        assertThat(entity.getRole()).isEqualTo("tool");

        ChatMessageDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getMetadata()).isEqualTo(dto.getMetadata());
        assertThat(roundTripped.getRole()).isEqualTo(ChatMessageRole.TOOL);
    }

    @Test
    void nullMetadataRoundTripsToNull() {
        ChatMessageEntity entity = ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .role("user")
                .content("hi")
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatMessageDTO dto = converter.toDTO(entity);
        assertThat(dto.getMetadata()).isNull();

        ChatMessageEntity roundTripped = converter.toEntity(dto);
        assertThat(roundTripped.getMetadata()).isNull();
    }

}
