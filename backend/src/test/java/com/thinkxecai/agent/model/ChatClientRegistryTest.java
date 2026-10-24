package com.thinkxecai.agent.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Every model name an agent can be saved with resolves, and nothing else does (ADR 040). */
class ChatClientRegistryTest {

    private final ChatClient deepSeek = mock(ChatClient.class);
    private final ChatClient gemini = mock(ChatClient.class);
    private final ChatClient qwen = mock(ChatClient.class);
    private final ChatClientRegistry registry =
            new ChatClientRegistry(Map.of("deepseek-chat", deepSeek, "gemini-3.8-flash", gemini, "qwen3.8-flash", qwen));

    @Test
    void eachModelNameResolvesToItsClient() {
        assertThat(registry.get("deepseek-chat")).isSameAs(deepSeek);
        assertThat(registry.get("gemini-3.8-flash")).isSameAs(gemini);
        assertThat(registry.get("qwen3.8-flash")).isSameAs(qwen);
    }

    @Test
    void anUnknownNameFailsNamingTheRegisteredOnes() {
        assertThatThrownBy(() -> registry.get("gpt-5.4-mini"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown chat model: gpt-5.4-mini (registered: deepseek-chat, gemini-3.8-flash, qwen3.8-flash)");
    }

    @Test
    void namesAreMatchedExactly() {
        assertThatThrownBy(() -> registry.get("DeepSeek-Chat")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.get(" deepseek-chat")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noNameFails() {
        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown chat model: null");
    }

    @Test
    void aMissingClientFailsAtStartup() {
        assertThatThrownBy(() -> new ChatClientRegistry(Map.of("deepseek-chat", deepSeek, "gemini-3.8-flash", gemini)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing: [qwen3.8-flash]");
    }

    @Test
    void anUnexpectedClientFailsAtStartup() {
        Map<String, ChatClient> clients = Map.of("deepseek-chat", deepSeek, "gemini-3.8-flash", gemini,
                "qwen3.8-flash", qwen, "gpt-5.4-mini", mock(ChatClient.class));

        assertThatThrownBy(() -> new ChatClientRegistry(clients))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected: [gpt-5.4-mini]");
    }

}
