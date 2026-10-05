package com.thinkxecai.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.dto.ChatSessionDTO;
import com.thinkxecai.model.dto.DocumentDTO;
import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.request.UpdateChatMessageRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database owns created_at / updated_at (column defaults on insert, updated_at = now() in
 * UPDATE statements). Converters must leave timestamps unset on create and untouched on update.
 */
class ConverterTimestampTest {

    private static final LocalDateTime STORED_CREATED = LocalDateTime.of(2026, 1, 1, 9, 0);
    private static final LocalDateTime STORED_UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void agentConverterLeavesTimestampsToTheDatabase() {
        AgentConverter converter = new AgentConverter(objectMapper);

        CreateAgentRequest create = new CreateAgentRequest();
        create.setName("agent");
        create.setModel("deepseek-chat");
        AgentDTO created = converter.fromCreateRequest(create);
        assertThat(created.getCreatedAt()).isNull();
        assertThat(created.getUpdatedAt()).isNull();

        UpdateAgentRequest update = new UpdateAgentRequest();
        update.setName("renamed");
        AgentDTO updated = converter.applyUpdateRequest(
                AgentDTO.builder().createdAt(STORED_CREATED).updatedAt(STORED_UPDATED).build(), update);
        assertThat(updated.getName()).isEqualTo("renamed");
        assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED);
        assertThat(updated.getUpdatedAt()).isEqualTo(STORED_UPDATED);
    }

    @Test
    void chatSessionConverterLeavesTimestampsToTheDatabase() {
        ChatSessionConverter converter = new ChatSessionConverter();

        CreateChatSessionRequest create = new CreateChatSessionRequest();
        create.setTitle("session");
        ChatSessionDTO created = converter.fromCreateRequest(create);
        assertThat(created.getCreatedAt()).isNull();
        assertThat(created.getUpdatedAt()).isNull();

        UpdateChatSessionRequest update = new UpdateChatSessionRequest();
        update.setTitle("renamed");
        ChatSessionDTO updated = converter.applyUpdateRequest(
                ChatSessionDTO.builder().createdAt(STORED_CREATED).updatedAt(STORED_UPDATED).build(), update);
        assertThat(updated.getTitle()).isEqualTo("renamed");
        assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED);
        assertThat(updated.getUpdatedAt()).isEqualTo(STORED_UPDATED);
    }

    @Test
    void chatMessageConverterLeavesTimestampsToTheDatabase() {
        ChatMessageConverter converter = new ChatMessageConverter(objectMapper);

        CreateChatMessageRequest create = new CreateChatMessageRequest();
        create.setSessionId(UUID.randomUUID().toString());
        create.setRole(ChatMessageRole.USER);
        create.setContent("hi");
        ChatMessageDTO created = converter.fromCreateRequest(create);
        assertThat(created.getCreatedAt()).isNull();
        assertThat(created.getUpdatedAt()).isNull();

        UpdateChatMessageRequest update = new UpdateChatMessageRequest();
        update.setContent("edited");
        ChatMessageDTO updated = converter.applyUpdateRequest(
                ChatMessageDTO.builder().createdAt(STORED_CREATED).updatedAt(STORED_UPDATED).build(), update);
        assertThat(updated.getContent()).isEqualTo("edited");
        assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED);
        assertThat(updated.getUpdatedAt()).isEqualTo(STORED_UPDATED);
    }

    @Test
    void knowledgeBaseConverterLeavesTimestampsToTheDatabase() {
        KnowledgeBaseConverter converter = new KnowledgeBaseConverter();

        CreateKnowledgeBaseRequest create = new CreateKnowledgeBaseRequest();
        create.setName("kb");
        KnowledgeBaseDTO created = converter.fromCreateRequest(create);
        assertThat(created.getCreatedAt()).isNull();
        assertThat(created.getUpdatedAt()).isNull();

        UpdateKnowledgeBaseRequest update = new UpdateKnowledgeBaseRequest();
        update.setName("renamed");
        KnowledgeBaseDTO updated = converter.applyUpdateRequest(
                KnowledgeBaseDTO.builder().createdAt(STORED_CREATED).updatedAt(STORED_UPDATED).build(), update);
        assertThat(updated.getName()).isEqualTo("renamed");
        assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED);
        assertThat(updated.getUpdatedAt()).isEqualTo(STORED_UPDATED);
    }

    @Test
    void documentConverterLeavesTimestampsToTheDatabase() {
        DocumentConverter converter = new DocumentConverter(objectMapper);

        CreateDocumentRequest create = new CreateDocumentRequest();
        create.setKbId(UUID.randomUUID().toString());
        create.setName("doc.md");
        DocumentDTO created = converter.fromCreateRequest(create);
        assertThat(created.getCreatedAt()).isNull();
        assertThat(created.getUpdatedAt()).isNull();

        UpdateDocumentRequest update = new UpdateDocumentRequest();
        update.setName("renamed.md");
        DocumentDTO updated = converter.applyUpdateRequest(
                DocumentDTO.builder().createdAt(STORED_CREATED).updatedAt(STORED_UPDATED).build(), update);
        assertThat(updated.getName()).isEqualTo("renamed.md");
        assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED);
        assertThat(updated.getUpdatedAt()).isEqualTo(STORED_UPDATED);
    }

}
