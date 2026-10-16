package com.thinkxecai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.entity.ChatMessageEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageMapperTest extends MapperTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ChatMessageMapper mapper;

    @Test
    void insertReadsBackGeneratedIdAndTimestamps() {
        ChatMessageEntity message = insert(insertChatSession(null, "s"), "user", "hello");

        assertThat(message.getId()).isNotNull();
        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getUpdatedAt()).isNotNull();
        assertThat(mapper.selectById(message.getId()).getContent()).isEqualTo("hello");
    }

    @Test
    void listBySessionIdReturnsOnlyThatSessionInInsertOrder() {
        UUID session = insertChatSession(null, "s");
        UUID otherSession = insertChatSession(null, "other");
        // All inserts share one test transaction; clock_timestamp() still keeps them in order.
        insert(session, "user", "first");
        insert(otherSession, "user", "not mine");
        insert(session, "assistant", "second");
        insert(session, "tool", "third");

        List<String> contents = contents(mapper.listBySessionId(session));

        assertThat(contents).containsExactly("first", "second", "third");
    }

    @Test
    void selectRecentReturnsTheLatestMessagesOldestFirst() {
        UUID session = insertChatSession(null, "s");
        IntStream.rangeClosed(1, 15).forEach(i -> insert(session, "user", "m" + i));

        List<String> recent = contents(mapper.selectRecentBySessionId(session, 10));

        assertThat(recent).containsExactly("m6", "m7", "m8", "m9", "m10", "m11", "m12", "m13", "m14", "m15");
    }

    @Test
    void selectRecentWithLimitAboveRowCountReturnsAllMessages() {
        UUID session = insertChatSession(null, "s");
        insert(session, "user", "m1");
        insert(session, "assistant", "m2");
        insert(session, "user", "m3");

        assertThat(contents(mapper.selectRecentBySessionId(session, 10))).containsExactly("m1", "m2", "m3");
    }

    @Test
    void metadataRoundTripsByJsonContentAndNullStaysNull() throws Exception {
        UUID session = insertChatSession(null, "s");
        String metadata = "{\"toolCalls\":[{\"id\":\"call-1\",\"type\":\"function\",\"name\":\"getDate\",\"arguments\":\"{}\"}]}";
        ChatMessageEntity withMetadata = insert(session, "assistant", "", metadata);
        ChatMessageEntity withoutMetadata = insert(session, "user", "hi");

        assertThat(JSON.readTree(mapper.selectById(withMetadata.getId()).getMetadata()))
                .isEqualTo(JSON.readTree(metadata));
        assertThat(mapper.selectById(withoutMetadata.getId()).getMetadata()).isNull();
    }

    @Test
    void deletingASessionDeletesItsMessages() {
        UUID session = insertChatSession(null, "s");
        insert(session, "user", "m1");
        insert(session, "assistant", "m2");

        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", session);

        assertThat(mapper.listBySessionId(session)).isEmpty();
    }

    @Test
    void deleteByIdRemovesTheRow() {
        ChatMessageEntity message = insert(insertChatSession(null, "s"), "user", "temporary");

        assertThat(mapper.deleteById(message.getId())).isEqualTo(1);
        assertThat(mapper.selectById(message.getId())).isNull();
    }

    @Test
    void unknownRoleIsRejectedByTheDatabase() {
        // Last statement on purpose: a failed statement aborts the rest of the test transaction.
        UUID session = insertChatSession(null, "s");

        assertThatThrownBy(() -> insert(session, "moderator", "not a valid role"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ChatMessageEntity insert(UUID sessionId, String role, String content) {
        return insert(sessionId, role, content, null);
    }

    private ChatMessageEntity insert(UUID sessionId, String role, String content, String metadata) {
        ChatMessageEntity message = ChatMessageEntity.builder()
                .sessionId(sessionId).role(role).content(content).metadata(metadata).build();
        mapper.insert(message);
        return message;
    }

    private static List<String> contents(List<ChatMessageEntity> messages) {
        return messages.stream().map(ChatMessageEntity::getContent).toList();
    }

}
