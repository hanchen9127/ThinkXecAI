package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseMapperTest extends MapperTestSupport {

    @Autowired
    private KnowledgeBaseMapper mapper;

    @Test
    void insertReadsBackGeneratedIdAndTimestamps() {
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().name("Docs").description("Product docs").build();

        assertThat(mapper.insert(kb)).isEqualTo(1);

        assertThat(kb.getId()).isNotNull();
        assertThat(kb.getCreatedAt()).isNotNull();
        assertThat(kb.getUpdatedAt()).isEqualTo(kb.getCreatedAt());
    }

    @Test
    void selectByIdReturnsTheRowOrNull() {
        KnowledgeBaseEntity kb = insert("Docs", "Product docs");

        KnowledgeBaseEntity found = mapper.selectById(kb.getId());

        assertThat(found).isEqualTo(kb);
        assertThat(mapper.selectById(UUID.randomUUID())).isNull();
    }

    @Test
    void listAllOrdersByUpdatedAtDescending() {
        UUID oldest = insert("oldest", null).getId();
        UUID newest = insert("newest", null).getId();
        UUID middle = insert("middle", null).getId();
        // now() is fixed within the test transaction, so give the rows distinct timestamps.
        setUpdatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setUpdatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setUpdatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listAll().stream()
                .map(KnowledgeBaseEntity::getId)
                .filter(Set.of(oldest, newest, middle)::contains)
                .toList();

        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    @Test
    void selectByIdsReturnsOnlyExistingRequestedRows() {
        UUID a = insert("a", null).getId();
        UUID b = insert("b", null).getId();
        insert("not requested", null);

        List<KnowledgeBaseEntity> found = mapper.selectByIds(new UUID[]{a, b, UUID.randomUUID()});

        assertThat(found).extracting(KnowledgeBaseEntity::getId).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void selectByIdsBindsAnyNumberOfIdsAsOneArray() {
        UUID a = insert("a", null).getId();
        // more ids than a statement may have parameters (65,535): one per id would be refused by the driver
        UUID[] ids = Stream.concat(Stream.of(a), Stream.generate(UUID::randomUUID).limit(70_000)).toArray(UUID[]::new);

        assertThat(mapper.selectByIds(ids)).extracting(KnowledgeBaseEntity::getId).containsExactly(a);
    }

    @Test
    void selectByIdsWithNoIdsReturnsEmptyList() {
        insert("exists", null);

        assertThat(mapper.selectByIds(new UUID[0])).isEmpty();
    }

    @Test
    void updateChangesOnlyNonNullFieldsAndRefreshesUpdatedAt() {
        KnowledgeBaseEntity kb = insert("Docs", "Product docs");
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        setUpdatedAt(kb.getId(), longAgo);

        int updated = mapper.update(KnowledgeBaseEntity.builder().id(kb.getId()).name("Renamed").build());

        KnowledgeBaseEntity found = mapper.selectById(kb.getId());
        assertThat(updated).isEqualTo(1);
        assertThat(found.getName()).isEqualTo("Renamed");
        assertThat(found.getDescription()).isEqualTo("Product docs");
        assertThat(found.getCreatedAt()).isEqualTo(kb.getCreatedAt());
        assertThat(found.getUpdatedAt()).isAfter(longAgo);
    }

    @Test
    void deleteByIdRemovesTheRow() {
        KnowledgeBaseEntity kb = insert("Docs", null);

        assertThat(mapper.deleteById(kb.getId())).isEqualTo(1);
        assertThat(mapper.selectById(kb.getId())).isNull();
        assertThat(mapper.deleteById(kb.getId())).isZero();
    }

    private KnowledgeBaseEntity insert(String name, String description) {
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().name(name).description(description).build();
        mapper.insert(kb);
        return kb;
    }

    private void setUpdatedAt(UUID id, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE knowledge_base SET updated_at = ? WHERE id = ?", updatedAt, id);
    }

}
