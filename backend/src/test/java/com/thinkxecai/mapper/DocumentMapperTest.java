package com.thinkxecai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMapperTest extends MapperTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String METADATA = "{\"filePath\":\"kb-1/doc-1/0f9c.md\",\"size\":2048}";

    @Autowired
    private DocumentMapper mapper;

    @Test
    void insertReadsBackGeneratedIdAndTimestamps() {
        DocumentEntity document = insert(insertKnowledgeBase("kb"), "guide.md", METADATA);

        assertThat(document.getId()).isNotNull();
        assertThat(document.getCreatedAt()).isNotNull();
        assertThat(document.getUpdatedAt()).isEqualTo(document.getCreatedAt());
    }

    @Test
    void metadataRoundTripsByJsonContentAndNullStaysNull() throws Exception {
        UUID kb = insertKnowledgeBase("kb");
        DocumentEntity withMetadata = insert(kb, "guide.md", METADATA);
        DocumentEntity withoutMetadata = insert(kb, "notes.md", null);

        assertThat(JSON.readTree(mapper.selectById(withMetadata.getId()).getMetadata()))
                .isEqualTo(JSON.readTree(METADATA));
        assertThat(mapper.selectById(withoutMetadata.getId()).getMetadata()).isNull();
        assertThat(mapper.selectById(UUID.randomUUID())).isNull();
    }

    @Test
    void listByKbIdReturnsOnlyThatKnowledgeBasesDocumentsNewestFirst() {
        UUID kb = insertKnowledgeBase("kb");
        UUID otherKb = insertKnowledgeBase("other");
        UUID older = insert(kb, "older.md", null).getId();
        UUID newer = insert(kb, "newer.md", null).getId();
        insert(otherKb, "someone else's.md", null);
        setUpdatedAt(older, LocalDateTime.of(2026, 1, 1, 0, 0));
        setUpdatedAt(newer, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listByKbId(kb).stream().map(DocumentEntity::getId).toList();

        assertThat(ids).containsExactly(newer, older);
    }

    @Test
    void listAllOrdersByUpdatedAtDescending() {
        UUID kb = insertKnowledgeBase("kb");
        UUID oldest = insert(kb, "oldest.md", null).getId();
        UUID newest = insert(kb, "newest.md", null).getId();
        UUID middle = insert(kb, "middle.md", null).getId();
        // now() is fixed within the test transaction, so give the rows distinct timestamps.
        setUpdatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setUpdatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setUpdatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listAll().stream()
                .map(DocumentEntity::getId)
                .filter(Set.of(oldest, newest, middle)::contains)
                .toList();

        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    @Test
    void updateChangesOnlyNonNullFieldsAndRefreshesUpdatedAt() throws Exception {
        DocumentEntity document = insert(insertKnowledgeBase("kb"), "guide.md", METADATA);
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        setUpdatedAt(document.getId(), longAgo);

        int updated = mapper.update(DocumentEntity.builder().id(document.getId()).name("renamed.md").build());

        DocumentEntity found = mapper.selectById(document.getId());
        assertThat(updated).isEqualTo(1);
        assertThat(found.getName()).isEqualTo("renamed.md");
        assertThat(JSON.readTree(found.getMetadata())).isEqualTo(JSON.readTree(METADATA));
        assertThat(found.getKbId()).isEqualTo(document.getKbId());
        assertThat(found.getCreatedAt()).isEqualTo(document.getCreatedAt());
        assertThat(found.getUpdatedAt()).isAfter(longAgo);
    }

    @Test
    void deletingAKnowledgeBaseDeletesItsDocuments() {
        UUID kb = insertKnowledgeBase("kb");
        insert(kb, "a.md", null);
        insert(kb, "b.md", null);

        jdbcTemplate.update("DELETE FROM knowledge_base WHERE id = ?", kb);

        assertThat(mapper.listByKbId(kb)).isEmpty();
    }

    @Test
    void deleteByIdRemovesTheRow() {
        DocumentEntity document = insert(insertKnowledgeBase("kb"), "temporary.md", null);

        assertThat(mapper.deleteById(document.getId())).isEqualTo(1);
        assertThat(mapper.selectById(document.getId())).isNull();
        assertThat(mapper.deleteById(document.getId())).isZero();
    }

    private DocumentEntity insert(UUID kbId, String name, String metadata) {
        DocumentEntity document = DocumentEntity.builder().kbId(kbId).name(name).metadata(metadata).build();
        mapper.insert(document);
        return document;
    }

    private void setUpdatedAt(UUID id, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE document SET updated_at = ? WHERE id = ?", updatedAt, id);
    }

}
