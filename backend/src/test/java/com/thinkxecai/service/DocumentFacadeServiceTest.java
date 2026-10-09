package com.thinkxecai.service;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFacadeServiceTest extends ServiceTestSupport {

    @Autowired
    private DocumentFacadeService service;

    @Test
    void createReturnsTheStoredRecordWithoutMetadata() {
        UUID kb = insertKnowledgeBase("Docs");

        DocumentVO created = service.createDocument(createRequest(kb, "guide.md"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getKbId()).isEqualTo(kb.toString());
        assertThat(created.getName()).isEqualTo("guide.md");
        assertThat(created.getMetadata()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void createWithUnknownKnowledgeBaseIsRejectedAndWritesNothing() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.createDocument(createRequest(unknown, "rejected.md")))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("kbId: unknown knowledge base " + unknown);
                });
        assertThat(countRows("document", "name = ?", "rejected.md")).isZero();
    }

    @Test
    void listByKbReturnsOnlyThatKnowledgeBasesDocuments() {
        UUID kb = insertKnowledgeBase("Docs");
        UUID own = insertDocument(kb, "own.md");
        insertDocument(insertKnowledgeBase("Other"), "other.md");

        assertThat(service.listDocumentsByKb(kb))
                .extracting(DocumentVO::getId)
                .containsExactly(own.toString());
        assertThat(service.listDocuments())
                .extracting(DocumentVO::getId)
                .contains(own.toString());
    }

    @Test
    void listByUnknownKnowledgeBaseIsNotFound() {
        assertCode(404, () -> service.listDocumentsByKb(UUID.randomUUID()));
    }

    @Test
    void updateRenamesAndKeepsServerManagedMetadata() {
        UUID document = insertDocument(insertKnowledgeBase("Docs"), "old.md");
        jdbcTemplate.update("UPDATE document SET metadata = '{\"filePath\": \"kb/doc/file.md\"}'::jsonb, updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 1, 1, 0, 0), document);

        DocumentVO updated = service.updateDocument(document, updateRequest("new.md"));

        assertThat(updated.getName()).isEqualTo("new.md");
        assertThat(updated.getMetadata()).isEqualTo(Map.of("filePath", "kb/doc/file.md"));
        assertThat(updated.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void updateOfUnknownIdIsNotFound() {
        assertCode(404, () -> service.updateDocument(UUID.randomUUID(), updateRequest("new.md")));
    }

    @Test
    void deleteRemovesTheDocumentAndItsChunks() {
        UUID kb = insertKnowledgeBase("Docs");
        UUID document = insertDocument(kb, "guide.md");
        jdbcTemplate.update("INSERT INTO chunk_bge_m3 (kb_id, doc_id, title, content) VALUES (?, ?, 'Intro', 'text')", kb, document);

        service.deleteDocument(document);

        assertThat(countRows("document", "id = ?", document)).isZero();
        assertThat(countRows("chunk_bge_m3", "doc_id = ?", document)).isZero();
    }

    @Test
    void deleteOfUnknownIdIsNotFound() {
        assertCode(404, () -> service.deleteDocument(UUID.randomUUID()));
    }

    private static CreateDocumentRequest createRequest(UUID kbId, String name) {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setKbId(kbId);
        request.setName(name);
        return request;
    }

    private static UpdateDocumentRequest updateRequest(String name) {
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setName(name);
        return request;
    }

    private static void assertCode(int code, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

}
