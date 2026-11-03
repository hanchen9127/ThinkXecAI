package com.thinkxecai.agent;

import com.sun.net.httpserver.HttpServer;
import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application starts without a Gemini key and keeps Gemini's beans, but the model is not configured:
 * an agent on it is answered with the "model is not available" notice, while the other models resolve
 * (ADR 050). Every model endpoint points at a local server that records requests, so the tests also show
 * that startup and the refused Gemini run send nothing to any model.
 */
@TestPropertySource(properties = "spring.ai.google.genai.api-key=")
class GeminiWithoutKeyTest extends ServiceTestSupport {

    private static final List<String> requests = new CopyOnWriteArrayList<>();
    private static final HttpServer modelServer = startModelServer();

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @Autowired
    private ThinkXecGoogleGenAiChatModel geminiChatModel;

    @Autowired
    private AgentRunner runner;

    @DynamicPropertySource
    static void pointModelsAtRecordingServer(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + modelServer.getAddress().getPort();
        registry.add("thinkxecai.chat.gemini-base-url", () -> url);
        registry.add("spring.ai.deepseek.base-url", () -> url);
        registry.add("thinkxecai.chat.qwen.base-url", () -> url);
        registry.add("spring.ai.ollama.base-url", () -> url);
    }

    @AfterAll
    static void stopModelServer() {
        modelServer.stop(0);
    }

    @Test
    void geminiIsRegisteredButNotConfiguredAndStartupCalledNoModel() {
        assertThat(geminiChatModel).isNotNull();
        assertThatThrownBy(() -> chatClientRegistry.get("gemini-3.8-flash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
        assertThat(chatClientRegistry.get("deepseek-chat")).isNotNull();
        assertThat(chatClientRegistry.get("qwen3.8-flash")).isNotNull();
        assertThat(requests).isEmpty();
    }

    @Test
    void aRunOnGeminiLeavesTheModelUnavailableNoticeWithoutCallingAnyModel() {
        UUID agent = jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model) VALUES ('on gemini', 'gemini-3.8-flash') RETURNING id", UUID.class);
        UUID session = insertChatSession(agent, "chat");
        UUID question = jdbcTemplate.queryForObject("INSERT INTO chat_message (session_id, role, content) "
                + "VALUES (?, 'user', 'Hello') RETURNING id", UUID.class, session);

        runner.run(agent, session, question, () -> false);

        assertThat(jdbcTemplate.queryForObject("SELECT content FROM chat_message WHERE session_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, session)).isEqualTo(AgentNotices.MODEL_UNAVAILABLE);
        assertThat(requests).isEmpty();
    }

    /** Records every request and answers 500, so a call that should not happen also fails loudly. */
    private static HttpServer startModelServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the recording model server", e);
        }
    }

}
