package com.thinkxecai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseFacadeServiceTest extends ServiceTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private KnowledgeBaseFacadeService service;

    @Test
    void createReturnsTheStoredKnowledgeBase() {
        KnowledgeBaseVO created = service.createKnowledgeBase(createRequest("Docs", "Product docs"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Docs");
        assertThat(created.getDescription()).isEqualTo("Product docs");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(countRows("knowledge_base", "id = ?", UUID.fromString(created.getId()))).isEqualTo(1);
    }

    @Test
    void listReturnsCreatedKnowledgeBases() {
        KnowledgeBaseVO created = service.createKnowledgeBase(createRequest("Docs", null));

        assertThat(service.listKnowledgeBases())
                .extracting(KnowledgeBaseVO::getId)
                .contains(created.getId());
    }

    @Test
    void updateChangesOnlyGivenFieldsAndReturnsTheStoredRow() {
        UUID id = UUID.fromString(service.createKnowledgeBase(createRequest("Docs", "Product docs")).getId());
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE knowledge_base SET updated_at = ? WHERE id = ?", old, id);
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("Manuals");

        KnowledgeBaseVO updated = service.updateKnowledgeBase(id, request);

        assertThat(updated.getName()).isEqualTo("Manuals");
        assertThat(updated.getDescription()).isEqualTo("Product docs");
        assertThat(updated.getUpdatedAt()).isAfter(old);
    }

    @Test
    void updateOfUnknownIdIsNotFound() {
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("Manuals");

        assertNotFound(() -> service.updateKnowledgeBase(UUID.randomUUID(), request));
    }

    @Test
    void deleteRemovesDocumentsAndAgentReferences() {
        UUID deleted = insertKnowledgeBase("deleted");
        UUID kept = insertKnowledgeBase("kept");
        insertDocument(deleted, "guide.md");
        UUID agent = insertAgentWithAllowedKbs("[\"" + kept + "\", \"" + deleted + "\"]");

        service.deleteKnowledgeBase(deleted);

        assertThat(countRows("knowledge_base", "id = ?", deleted)).isZero();
        assertThat(countRows("document", "kb_id = ?", deleted)).isZero();
        assertThat(allowedKbs(agent)).isEqualTo(json("[\"" + kept + "\"]"));
    }

    @Test
    void deleteOfUnknownIdIsNotFoundAndChangesNothing() {
        UUID unknown = UUID.randomUUID();
        UUID agent = insertAgentWithAllowedKbs("[\"" + unknown + "\"]");

        assertNotFound(() -> service.deleteKnowledgeBase(unknown));

        assertThat(allowedKbs(agent)).isEqualTo(json("[\"" + unknown + "\"]"));
    }

    private static CreateKnowledgeBaseRequest createRequest(String name, String description) {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private UUID insertAgentWithAllowedKbs(String allowedKbs) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model, allowed_kbs) VALUES ('agent', 'deepseek-chat', ?::jsonb) RETURNING id",
                UUID.class, allowedKbs);
    }

    private JsonNode allowedKbs(UUID agentId) {
        return json(jdbcTemplate.queryForObject("SELECT allowed_kbs::text FROM agent WHERE id = ?", String.class, agentId));
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not JSON: " + text, e);
        }
    }

}
