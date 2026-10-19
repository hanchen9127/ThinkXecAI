package com.thinkxecai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.agent.GeneratedMessages;
import com.thinkxecai.exception.ChatSessionGoneException;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.vo.ChatMessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an agent run generates is stored as assistant and tool rows with typed metadata (ADR 031). */
class GeneratedMessageServiceTest extends ServiceTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private GeneratedMessageService service;

    private static AssistantMessage calling(String text, String... ids) {
        return AssistantMessage.builder()
                .content(text)
                .toolCalls(Arrays.stream(ids)
                        .map(id -> new AssistantMessage.ToolCall(id, "function", "weather", "{\"city\":\"Sydney\"}"))
                        .toList())
                .build();
    }

    @Test
    void anAssistantMessageWithToolCallsIsStoredWithItsCalls() throws Exception {
        UUID session = insertChatSession(insertAgent("Research"), "chat");

        List<ChatMessageVO> saved = service.save(session, List.of(GeneratedMessages.assistant(calling("Checking.", "call_1"))));

        assertThat(saved).singleElement().satisfies(vo -> {
            assertThat(vo.getId()).isNotNull();
            assertThat(vo.getSessionId()).isEqualTo(session.toString());
            assertThat(vo.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
            assertThat(vo.getContent()).isEqualTo("Checking.");
            assertThat(vo.getCreatedAt()).isNotNull();
        });
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM chat_message WHERE session_id = ?", String.class, session);
        assertThat(JSON.readTree(metadata)).isEqualTo(JSON.readTree(
                "{\"toolCalls\":[{\"id\":\"call_1\",\"type\":\"function\",\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"Sydney\\\"}\"}]}"));
    }

    @Test
    void eachToolResultIsOneToolRowInOrder() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        ToolResponseMessage results = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("a", "getCity", "Sydney"),
                new ToolResponseMessage.ToolResponse("b", "getDate", "2026-10-19"))).build();

        List<ChatMessageVO> saved = service.save(session, GeneratedMessages.toolResults(results));

        assertThat(saved).extracting(ChatMessageVO::getRole).containsOnly(ChatMessageRole.TOOL);
        assertThat(saved).extracting(ChatMessageVO::getContent).containsExactly("Sydney", "2026-10-19");
        assertThat(saved).extracting(vo -> vo.getMetadata().toolResponse().id()).containsExactly("a", "b");
        List<String> stored = jdbcTemplate.queryForList(
                "SELECT content FROM chat_message WHERE session_id = ? ORDER BY created_at", String.class, session);
        assertThat(stored).containsExactly("Sydney", "2026-10-19");
    }

    @Test
    void aNoticeIsAPlainAssistantMessage() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");

        ChatMessageVO saved = service.save(session, List.of(GeneratedMessages.notice("The agent stopped."))).get(0);

        assertThat(saved.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(saved.getMetadata()).isNull();
        assertThat(countRows("chat_message", "session_id = ? AND metadata IS NULL", session)).isOne();
    }

    @Test
    void savingRefreshesTheSessionsUpdatedAt() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", old, session);

        service.save(session, List.of(GeneratedMessages.notice("Done.")));

        LocalDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM chat_session WHERE id = ?", LocalDateTime.class, session);
        assertThat(updatedAt).isAfter(old);
    }

    @Test
    void aSessionThatNoLongerExistsSavesNothing() {
        UUID gone = UUID.randomUUID();

        assertThatThrownBy(() -> service.save(gone, List.of(GeneratedMessages.notice("Done."))))
                .isInstanceOf(ChatSessionGoneException.class);
        assertThat(countRows("chat_message", "session_id = ?", gone)).isZero();
    }

    @Test
    void userAndSystemMessagesAreRefusedBeforeAnythingIsWritten() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        ChatMessageDTO user = ChatMessageDTO.builder().role(ChatMessageRole.USER).content("hi").build();

        assertThatThrownBy(() -> service.save(session, List.of(GeneratedMessages.notice("ok"), user)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(countRows("chat_message", "session_id = ?", session)).isZero();
    }

    @Test
    void theGivenSessionIdWinsOverTheMessages() {
        UUID session = insertChatSession(insertAgent("Research"), "chat");
        ChatMessageDTO notice = GeneratedMessages.notice("ok");
        notice.setSessionId(insertChatSession(insertAgent("Other"), "other"));

        service.save(session, List.of(notice));

        assertThat(countRows("chat_message", "session_id = ?", session)).isOne();
    }

}
