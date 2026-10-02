package com.thinkxecai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.entity.ChatMessageEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatMessageConverter converter = new ChatMessageConverter(objectMapper);

    @Test
    void assistantToolCallsRoundTripBetweenDtoAndEntity() {
        ChatMessageMetadata metadata = new ChatMessageMetadata(List.of(
                new ToolCallMetadata("call-1", "function", "getDate", "{}"),
                new ToolCallMetadata("call-2", "function", "KnowledgeTool", "{\"kbsId\":\"kb-1\",\"query\":\"refund\"}")
        ), null);
        ChatMessageDTO dto = message(ChatMessageRole.ASSISTANT, "", metadata);

        ChatMessageEntity entity = converter.toEntity(dto);
        assertThat(entity.getRole()).isEqualTo("assistant");

        ChatMessageDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getMetadata()).isEqualTo(metadata);
        assertThat(roundTripped.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
    }

    @Test
    void toolResponseRoundTripsBetweenDtoAndEntity() {
        ChatMessageMetadata metadata = new ChatMessageMetadata(null,
                new ToolResponseMetadata("call-1", "getDate", "2026-10-03"));
        ChatMessageDTO dto = message(ChatMessageRole.TOOL, "2026-10-03", metadata);

        ChatMessageEntity entity = converter.toEntity(dto);
        assertThat(entity.getRole()).isEqualTo("tool");

        ChatMessageDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getMetadata()).isEqualTo(metadata);
        assertThat(roundTripped.getRole()).isEqualTo(ChatMessageRole.TOOL);
    }

    @Test
    void storedJsonUsesTheSpecShapeAndOmitsAbsentParts() throws Exception {
        ChatMessageDTO assistant = message(ChatMessageRole.ASSISTANT, "", new ChatMessageMetadata(
                List.of(new ToolCallMetadata("call-1", "function", "getDate", "{}")), null));
        ChatMessageDTO tool = message(ChatMessageRole.TOOL, "2026-10-03", new ChatMessageMetadata(
                null, new ToolResponseMetadata("call-1", "getDate", "2026-10-03")));

        JsonNode assistantJson = objectMapper.readTree(converter.toEntity(assistant).getMetadata());
        assertThat(fieldNames(assistantJson)).containsExactly("toolCalls");
        assertThat(fieldNames(assistantJson.get("toolCalls").get(0)))
                .containsExactlyInAnyOrder("id", "type", "name", "arguments");

        JsonNode toolJson = objectMapper.readTree(converter.toEntity(tool).getMetadata());
        assertThat(fieldNames(toolJson)).containsExactly("toolResponse");
        assertThat(fieldNames(toolJson.get("toolResponse")))
                .containsExactlyInAnyOrder("id", "name", "responseData");
    }

    @Test
    void unknownJsonKeysAreIgnored() {
        ChatMessageEntity entity = entity("tool",
                "{\"toolResponse\":{\"id\":\"call-1\",\"name\":\"getDate\",\"responseData\":\"x\",\"extra\":1},\"legacy\":true}");

        ChatMessageDTO dto = converter.toDTO(entity);

        assertThat(dto.getMetadata().toolResponse()).isEqualTo(new ToolResponseMetadata("call-1", "getDate", "x"));
        assertThat(dto.getMetadata().toolCalls()).isNull();
    }

    @Test
    void nullMetadataRoundTripsToNull() {
        ChatMessageDTO dto = converter.toDTO(entity("user", null));
        assertThat(dto.getMetadata()).isNull();

        ChatMessageEntity roundTripped = converter.toEntity(dto);
        assertThat(roundTripped.getMetadata()).isNull();
    }

    private static ChatMessageDTO message(ChatMessageRole role, String content, ChatMessageMetadata metadata) {
        return ChatMessageDTO.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .role(role)
                .content(content)
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static ChatMessageEntity entity(String role, String metadataJson) {
        return ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .role(role)
                .content("content")
                .metadata(metadataJson)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

}
