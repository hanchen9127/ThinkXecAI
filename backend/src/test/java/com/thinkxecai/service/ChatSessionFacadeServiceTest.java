package com.thinkxecai.service;

import com.thinkxecai.converter.ChatSessionConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.ChatSessionMapper;
import com.thinkxecai.model.entity.ChatSessionEntity;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;
import com.thinkxecai.service.impl.ChatSessionFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionFacadeServiceTest extends ServiceTestSupport {

    @Autowired
    private ChatSessionFacadeService service;

    @Test
    void createWithAgentReturnsTheStoredSession() {
        UUID agent = insertAgent("Research");

        ChatSessionVO created = service.createChatSession(createRequest(agent, "First chat"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getAgentId()).isEqualTo(agent.toString());
        assertThat(created.getTitle()).isEqualTo("First chat");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(service.getChatSession(UUID.fromString(created.getId()))).isEqualTo(created);
    }

    @Test
    void createWithoutAgentIsAllowed() {
        ChatSessionVO created = service.createChatSession(createRequest(null, null));

        assertThat(created.getAgentId()).isNull();
        assertThat(created.getTitle()).isNull();
    }

    @Test
    void createWithUnknownAgentIsRejectedAndWritesNothing() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.createChatSession(createRequest(unknown, "Rejected")))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("agentId: unknown agent " + unknown);
                });
        assertThat(countRows("chat_session", "title = ?", "Rejected")).isZero();
    }

    @Test
    void getOfUnknownIdIsNotFound() {
        assertNotFound(() -> service.getChatSession(UUID.randomUUID()));
    }

    @Test
    void listByAgentReturnsOnlyThatAgentsSessions() {
        UUID agent = insertAgent("Research");
        UUID own = insertChatSession(agent, "own");
        insertChatSession(insertAgent("Other"), "other");

        assertThat(service.listChatSessionsByAgent(agent))
                .extracting(ChatSessionVO::getId)
                .containsExactly(own.toString());
    }

    @Test
    void listByUnknownAgentIsNotFound() {
        assertNotFound(() -> service.listChatSessionsByAgent(UUID.randomUUID()));
    }

    @Test
    void updateChangesTheTitleAndKeepsTheAgent() {
        UUID agent = insertAgent("Research");
        UUID session = insertChatSession(agent, "old title");
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", old, session);

        ChatSessionVO updated = service.updateChatSession(session, updateRequest("new title"));

        assertThat(updated.getTitle()).isEqualTo("new title");
        assertThat(updated.getAgentId()).isEqualTo(agent.toString());
        assertThat(updated.getUpdatedAt()).isAfter(old);
    }

    @Test
    void updateNeverWritesTheAgentColumn() {
        // The agent may be deleted between reading the session and updating it; writing the stale agent_id
        // back would then violate the foreign key. Mapper mocked: the race cannot be staged on one connection.
        ChatSessionMapper mapper = mock(ChatSessionMapper.class);
        UUID id = UUID.randomUUID();
        when(mapper.selectById(id)).thenReturn(
                ChatSessionEntity.builder().id(id).agentId(UUID.randomUUID()).title("old title").build());
        ChatSessionFacadeService unit = new ChatSessionFacadeServiceImpl(mapper, mock(AgentMapper.class), new ChatSessionConverter(),
                mock(SseService.class));

        unit.updateChatSession(id, updateRequest("new title"));

        verify(mapper).update(argThat(entity -> entity.getAgentId() == null && "new title".equals(entity.getTitle())));
    }

    @Test
    void updateOfUnknownIdIsNotFound() {
        assertNotFound(() -> service.updateChatSession(UUID.randomUUID(), updateRequest("title")));
    }

    @Test
    void deleteRemovesTheSessionAndItsMessages() {
        UUID session = insertChatSession(null, "chat");
        jdbcTemplate.update("INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', 'hi')", session);

        service.deleteChatSession(session);

        assertThat(countRows("chat_session", "id = ?", session)).isZero();
        assertThat(countRows("chat_message", "session_id = ?", session)).isZero();
    }

    @Test
    void deleteOfUnknownIdIsNotFound() {
        assertNotFound(() -> service.deleteChatSession(UUID.randomUUID()));
    }

    private static CreateChatSessionRequest createRequest(UUID agentId, String title) {
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        request.setAgentId(agentId);
        request.setTitle(title);
        return request;
    }

    private static UpdateChatSessionRequest updateRequest(String title) {
        UpdateChatSessionRequest request = new UpdateChatSessionRequest();
        request.setTitle(title);
        return request;
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

}
