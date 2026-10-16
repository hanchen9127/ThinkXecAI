package com.thinkxecai.service;

import com.thinkxecai.event.ChatEvent;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@RecordApplicationEvents
class ChatMessageFacadeServiceTest extends ServiceTestSupport {

    @Autowired
    private ChatMessageFacadeService service;

    @Autowired
    private ApplicationEvents events;

    @Test
    void messageIsStoredAsAUserMessageAndPublishesOneEventWithTheSessionsAgent() {
        UUID agent = insertAgent("Research");
        UUID session = insertChatSession(agent, "chat");

        ChatMessageVO created = service.createChatMessage(request(session, "Hello"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getSessionId()).isEqualTo(session.toString());
        assertThat(created.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(created.getContent()).isEqualTo("Hello");
        assertThat(created.getMetadata()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(countRows("chat_message", "session_id = ? AND role = 'user' AND metadata IS NULL", session)).isEqualTo(1);
        assertThat(events.stream(ChatEvent.class)).containsExactly(new ChatEvent(agent, session));
    }

    @Test
    void unknownSessionIsNotFoundAndPublishesNothing() {
        assertCode(404, () -> service.createChatMessage(request(UUID.randomUUID(), "Hello")));

        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    @Test
    void sessionWithoutAgentIsAConflictAndWritesNothing() {
        UUID session = insertChatSession(null, "orphaned");

        assertCode(409, () -> service.createChatMessage(request(session, "Hello")));

        assertThat(countRows("chat_message", "session_id = ?", session)).isZero();
        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    @Test
    void creatingAMessageRefreshesTheSessionsUpdatedAt() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", old, session);

        service.createChatMessage(request(session, "Hello"));

        LocalDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM chat_session WHERE id = ?", LocalDateTime.class, session);
        assertThat(updatedAt).isAfter(old);
    }

    @Test
    void listReturnsTheSessionsMessagesOldestFirstWithTheirMetadata() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        String first = service.createChatMessage(request(session, "first")).getId();
        // runtime-written rows: the API cannot create them (ADR 031)
        UUID second = insertMessage(session, "tool", "2026-10-08",
                "{\"toolResponse\":{\"id\":\"call_1\",\"name\":\"dateTool\",\"responseData\":\"2026-10-08\"}}");

        assertThat(service.listChatMessagesBySession(session))
                .extracting(ChatMessageVO::getId, ChatMessageVO::getRole, ChatMessageVO::getMetadata)
                .containsExactly(
                        tuple(first, ChatMessageRole.USER, null),
                        tuple(second.toString(), ChatMessageRole.TOOL, new ChatMessageMetadata(
                                null, new ToolResponseMetadata("call_1", "dateTool", "2026-10-08"))));
    }

    @Test
    void listOfUnknownSessionIsNotFound() {
        assertCode(404, () -> service.listChatMessagesBySession(UUID.randomUUID()));
    }

    @Test
    void deleteRemovesTheMessage() {
        UUID session = insertChatSession(null, "chat");
        UUID id = insertMessage(session, "assistant", "hi", null);

        service.deleteChatMessage(id);

        assertThat(countRows("chat_message", "id = ?", id)).isZero();
    }

    @Test
    void deleteOfUnknownIdIsNotFound() {
        assertCode(404, () -> service.deleteChatMessage(UUID.randomUUID()));
    }

    private UUID insertMessage(UUID sessionId, String role, String content, String metadata) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO chat_message (session_id, role, content, metadata) VALUES (?, ?, ?, ?::jsonb) RETURNING id",
                UUID.class, sessionId, role, content, metadata);
    }

    private static CreateChatMessageRequest request(UUID sessionId, String content) {
        CreateChatMessageRequest request = new CreateChatMessageRequest();
        request.setSessionId(sessionId);
        request.setContent(content);
        return request;
    }

    private static void assertCode(int code, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

}
