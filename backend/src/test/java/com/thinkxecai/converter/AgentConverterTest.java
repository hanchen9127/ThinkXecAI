package com.thinkxecai.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.ChatOptions;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.entity.AgentEntity;
import com.thinkxecai.model.request.CreateAgentRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConverterTest {

    private final AgentConverter converter = new AgentConverter(new ObjectMapper());

    @Test
    void jsonFieldsRoundTripBetweenDtoAndEntity() {
        AgentDTO dto = AgentDTO.builder()
                .id(UUID.randomUUID())
                .name("Research Agent")
                .description("Answers research questions")
                .systemPrompt("You are a helpful research assistant.")
                .model("deepseek-chat")
                .allowedTools(List.of("dataBaseTool"))
                .allowedKbs(List.of(UUID.randomUUID().toString()))
                .chatOptions(ChatOptions.builder().temperature(0.3).topP(0.9).messageLength(5).build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AgentEntity entity = converter.toEntity(dto);
        assertThat(entity.getAllowedTools()).contains("dataBaseTool");
        assertThat(entity.getChatOptions()).contains("0.3");

        AgentDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getAllowedTools()).isEqualTo(dto.getAllowedTools());
        assertThat(roundTripped.getAllowedKbs()).isEqualTo(dto.getAllowedKbs());
        assertThat(roundTripped.getChatOptions()).isEqualTo(dto.getChatOptions());
    }

    @Test
    void absentJsonFieldsStayNullSoTheColumnDefaultsApply() {
        // a JSON null would be stored instead of the column default (ADR 009) and read back as null
        AgentEntity entity = converter.toEntity(AgentDTO.builder().name("a").model("deepseek-chat").build());

        assertThat(entity.getAllowedTools()).isNull();
        assertThat(entity.getAllowedKbs()).isNull();
        assertThat(entity.getChatOptions()).isNull();
    }

    @Test
    void createRequestAppliesSpecDefaultsWhenOmitted() {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName("Bare Agent");
        request.setModel("gemini-3.8-flash");

        AgentDTO dto = converter.fromCreateRequest(request);

        assertThat(dto.getAllowedTools()).isEmpty();
        assertThat(dto.getAllowedKbs()).isEmpty();
        assertThat(dto.getChatOptions().getTemperature()).isEqualTo(0.7);
        assertThat(dto.getChatOptions().getTopP()).isEqualTo(1.0);
        assertThat(dto.getChatOptions().getMessageLength()).isEqualTo(10);
    }

}
