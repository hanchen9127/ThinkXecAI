package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.ChatSessionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionMapperTest extends MapperTestSupport {

    @Autowired
    private ChatSessionMapper mapper;

    @Test
    void insertReadsBackGeneratedIdAndTimestamps() {
        ChatSessionEntity session = insert(insertAgent("agent"), "Trip planning");

        assertThat(session.getId()).isNotNull();
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getUpdatedAt()).isEqualTo(session.getCreatedAt());
        assertThat(mapper.selectById(session.getId())).isEqualTo(session);
    }

    @Test
    void sessionCanExistWithoutAnAgent() {
        ChatSessionEntity session = insert(null, "No agent");

        assertThat(mapper.selectById(session.getId()).getAgentId()).isNull();
    }

    @Test
    void selectByIdReturnsNullForUnknownId() {
        assertThat(mapper.selectById(UUID.randomUUID())).isNull();
    }

    @Test
    void listByAgentIdReturnsOnlyThatAgentsSessionsNewestFirst() {
        UUID agent = insertAgent("agent");
        UUID otherAgent = insertAgent("other");
        UUID older = insert(agent, "older").getId();
        UUID newer = insert(agent, "newer").getId();
        insert(otherAgent, "someone else's");
        insert(null, "no agent");
        setUpdatedAt(older, LocalDateTime.of(2026, 1, 1, 0, 0));
        setUpdatedAt(newer, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listByAgentId(agent).stream().map(ChatSessionEntity::getId).toList();

        assertThat(ids).containsExactly(newer, older);
    }

    @Test
    void listAllOrdersByUpdatedAtDescending() {
        UUID oldest = insert(null, "oldest").getId();
        UUID newest = insert(null, "newest").getId();
        UUID middle = insert(null, "middle").getId();
        // now() is fixed within the test transaction, so give the rows distinct timestamps.
        setUpdatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setUpdatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setUpdatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listAll().stream()
                .map(ChatSessionEntity::getId)
                .filter(Set.of(oldest, newest, middle)::contains)
                .toList();

        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    @Test
    void updateChangesOnlyNonNullFieldsAndRefreshesUpdatedAt() {
        UUID agent = insertAgent("agent");
        ChatSessionEntity session = insert(agent, "Trip planning");
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        setUpdatedAt(session.getId(), longAgo);

        int updated = mapper.update(ChatSessionEntity.builder().id(session.getId()).title("Renamed").build());

        ChatSessionEntity found = mapper.selectById(session.getId());
        assertThat(updated).isEqualTo(1);
        assertThat(found.getTitle()).isEqualTo("Renamed");
        assertThat(found.getAgentId()).isEqualTo(agent);
        assertThat(found.getCreatedAt()).isEqualTo(session.getCreatedAt());
        assertThat(found.getUpdatedAt()).isAfter(longAgo);
    }

    @Test
    void updateNeverChangesTheAgent() {
        // the agent is fixed at creation (ADR 016), also when a caller passes one
        UUID agent = insertAgent("agent");
        ChatSessionEntity session = insert(agent, "Trip planning");

        mapper.update(ChatSessionEntity.builder().id(session.getId()).agentId(insertAgent("other")).title("Renamed").build());

        assertThat(mapper.selectById(session.getId()).getAgentId()).isEqualTo(agent);
    }

    @Test
    void touchRefreshesOnlyUpdatedAt() {
        UUID agent = insertAgent("agent");
        ChatSessionEntity session = insert(agent, "Trip planning");
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        setUpdatedAt(session.getId(), longAgo);

        assertThat(mapper.touch(session.getId())).isEqualTo(1);

        ChatSessionEntity found = mapper.selectById(session.getId());
        assertThat(found.getUpdatedAt()).isAfter(longAgo);
        assertThat(found.getTitle()).isEqualTo("Trip planning");
        assertThat(found.getAgentId()).isEqualTo(agent);
        assertThat(mapper.touch(UUID.randomUUID())).isZero();
    }

    @Test
    void deleteByIdRemovesTheRow() {
        ChatSessionEntity session = insert(null, "temporary");

        assertThat(mapper.deleteById(session.getId())).isEqualTo(1);
        assertThat(mapper.selectById(session.getId())).isNull();
        assertThat(mapper.deleteById(session.getId())).isZero();
    }

    private ChatSessionEntity insert(UUID agentId, String title) {
        ChatSessionEntity session = ChatSessionEntity.builder().agentId(agentId).title(title).build();
        mapper.insert(session);
        return session;
    }

    private void setUpdatedAt(UUID id, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", updatedAt, id);
    }

}
