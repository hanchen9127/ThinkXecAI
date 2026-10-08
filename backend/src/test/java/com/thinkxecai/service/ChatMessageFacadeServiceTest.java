package com.thinkxecai.service;

import com.thinkxecai.event.ChatEvent;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.request.UpdateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
class ChatMessageFacadeServiceTest extends ServiceTestSupport {

    @Autowired
    private ChatMessageFacadeService service;

    @Autowired
    private ApplicationEvents events;

    @Test
    void userMessageIsStoredAndPublishesOneEventWithTheSessionsAgent() {
        UUID agent = insertAgent("Research");
        UUID session = insertChatSession(agent, "chat");

        ChatMessageVO created = service.createChatMessage(request(session, ChatMessageRole.USER, "Hello"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getSessionId()).isEqualTo(session.toString());
        assertThat(created.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(created.getContent()).isEqualTo("Hello");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(events.stream(ChatEvent.class)).containsExactly(new ChatEvent(agent, session));
    }

    @ParameterizedTest
    @EnumSource(value = ChatMessageRole.class, names = {"ASSISTANT", "TOOL", "SYSTEM"})
    void otherRolesAreStoredWithoutAnEventEvenWithoutAnAgent(ChatMessageRole role) {
        UUID session = insertChatSession(null, "chat");

        service.createChatMessage(request(session, role, null));

        assertThat(countRows("chat_message", "session_id = ?", session)).isEqualTo(1);
        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    @Test
    void blankUserMessageIsRejectedAndWritesNothing() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");

        assertCode(400, () -> service.createChatMessage(request(session, ChatMessageRole.USER, "  ")));

        assertNothingWritten(session);
    }

    @Test
    void unknownSessionIsNotFoundAndPublishesNothing() {
        assertCode(404, () -> service.createChatMessage(request(UUID.randomUUID(), ChatMessageRole.USER, "Hello")));

        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    @Test
    void userMessageToASessionWithoutAgentIsAConflictAndWritesNothing() {
        UUID session = insertChatSession(null, "orphaned");

        assertCode(409, () -> service.createChatMessage(request(session, ChatMessageRole.USER, "Hello")));

        assertNothingWritten(session);
    }

    @Test
    void creatingAMessageRefreshesTheSessionsUpdatedAt() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", old, session);

        service.createChatMessage(request(session, ChatMessageRole.USER, "Hello"));

        LocalDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM chat_session WHERE id = ?", LocalDateTime.class, session);
        assertThat(updatedAt).isAfter(old);
    }

    @Test
    void metadataRoundTrips() {
        UUID session = insertChatSession(null, "chat");
        CreateChatMessageRequest request = request(session, ChatMessageRole.TOOL, null);
        request.setMetadata(new ChatMessageMetadata(null, new ToolResponseMetadata("call_1", "dateTool", "2026-10-08")));

        ChatMessageVO created = service.createChatMessage(request);

        assertThat(created.getMetadata()).isEqualTo(request.getMetadata());
    }

    @Test
    void listReturnsTheSessionsMessagesOldestFirst() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        String first = service.createChatMessage(request(session, ChatMessageRole.USER, "first")).getId();
        String second = service.createChatMessage(request(session, ChatMessageRole.ASSISTANT, "second")).getId();

        assertThat(service.listChatMessagesBySession(session))
                .extracting(ChatMessageVO::getId)
                .containsExactly(first, second);
    }

    @Test
    void listOfUnknownSessionIsNotFound() {
        assertCode(404, () -> service.listChatMessagesBySession(UUID.randomUUID()));
    }

    @Test
    void updateChangesContentKeepsMetadataAndPublishesNothing() {
        UUID session = insertChatSession(null, "chat");
        CreateChatMessageRequest create = request(session, ChatMessageRole.TOOL, "old");
        create.setMetadata(new ChatMessageMetadata(null, new ToolResponseMetadata("call_1", "dateTool", "x")));
        UUID id = UUID.fromString(service.createChatMessage(create).getId());
        UpdateChatMessageRequest update = new UpdateChatMessageRequest();
        update.setContent("new");

        ChatMessageVO updated = service.updateChatMessage(id, update);

        assertThat(updated.getContent()).isEqualTo("new");
        assertThat(updated.getMetadata()).isEqualTo(create.getMetadata());
        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    @Test
    void updateOfUnknownIdIsNotFound() {
        assertCode(404, () -> service.updateChatMessage(UUID.randomUUID(), new UpdateChatMessageRequest()));
    }

    @Test
    void deleteRemovesTheMessage() {
        UUID session = insertChatSession(null, "chat");
        UUID id = UUID.fromString(service.createChatMessage(request(session, ChatMessageRole.ASSISTANT, "hi")).getId());

        service.deleteChatMessage(id);

        assertThat(countRows("chat_message", "id = ?", id)).isZero();
    }

    @Test
    void deleteOfUnknownIdIsNotFound() {
        assertCode(404, () -> service.deleteChatMessage(UUID.randomUUID()));
    }

    private void assertNothingWritten(UUID session) {
        assertThat(countRows("chat_message", "session_id = ?", session)).isZero();
        assertThat(events.stream(ChatEvent.class)).isEmpty();
    }

    private static CreateChatMessageRequest request(UUID sessionId, ChatMessageRole role, String content) {
        CreateChatMessageRequest request = new CreateChatMessageRequest();
        request.setSessionId(sessionId);
        request.setRole(role);
        request.setContent(content);
        return request;
    }

    private static void assertCode(int code, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

}
