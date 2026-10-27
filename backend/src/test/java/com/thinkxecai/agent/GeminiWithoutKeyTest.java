package com.thinkxecai.agent;

import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application starts without a Gemini key and keeps Gemini's beans, but the model is not configured:
 * an agent on it is answered with the "model is not available" notice before anything is sent to Google,
 * while the other models resolve (ADR 050).
 */
@TestPropertySource(properties = "spring.ai.google.genai.api-key=")
class GeminiWithoutKeyTest extends ServiceTestSupport {

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @Autowired
    private ThinkXecGoogleGenAiChatModel geminiChatModel;

    @Autowired
    private AgentRunner runner;

    @Test
    void geminiIsRegisteredButNotConfigured() {
        assertThat(geminiChatModel).isNotNull();
        assertThatThrownBy(() -> chatClientRegistry.get("gemini-3.8-flash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
        assertThat(chatClientRegistry.get("deepseek-chat")).isNotNull();
        assertThat(chatClientRegistry.get("qwen3.8-flash")).isNotNull();
    }

    @Test
    void aRunOnGeminiLeavesTheModelUnavailableNotice() {
        UUID agent = jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model) VALUES ('on gemini', 'gemini-3.8-flash') RETURNING id", UUID.class);
        UUID session = insertChatSession(agent, "chat");
        UUID question = jdbcTemplate.queryForObject("INSERT INTO chat_message (session_id, role, content) "
                + "VALUES (?, 'user', 'Hello') RETURNING id", UUID.class, session);

        runner.run(agent, session, question);

        assertThat(jdbcTemplate.queryForObject("SELECT content FROM chat_message WHERE session_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, session)).isEqualTo(AgentNotices.MODEL_UNAVAILABLE);
    }

}
