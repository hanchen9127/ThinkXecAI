package com.thinkxecai.agent.model;

import com.thinkxecai.model.ChatModelNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the {@link ChatClient} for an agent's {@code model}. The clients are the beans named after
 * {@link ChatModelNames}; startup fails if one is missing or another is registered, so every name an
 * agent can be saved with resolves (ADR 040). An unknown name (only possible in a row written outside
 * the API) is an {@link IllegalArgumentException}, which the agent runtime reports as a failed run.
 * <p>
 * Gemini's API key is optional (ADR 050): without it Gemini is registered but not configured, and
 * {@link #get} refuses it the same way, so a run on it leaves the "model is not available" notice
 * instead of calling Google with an empty key.
 */
@Slf4j
@Component
public class ChatClientRegistry {

    private final Map<String, ChatClient> chatClients;
    private final Set<String> notConfigured;

    public ChatClientRegistry(Map<String, ChatClient> chatClients,
                              @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        List<String> missing = new ArrayList<>(ChatModelNames.ALL);
        missing.removeAll(chatClients.keySet());
        List<String> extra = new ArrayList<>(chatClients.keySet());
        extra.removeAll(ChatModelNames.ALL);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalStateException("Chat clients must be exactly " + ChatModelNames.ALL
                    + "; missing: " + missing + ", unexpected: " + extra);
        }
        this.chatClients = Map.copyOf(chatClients);
        this.notConfigured = StringUtils.hasText(geminiApiKey) ? Set.of() : Set.of(ChatModelNames.GEMINI_FLASH);
        if (!notConfigured.isEmpty()) {
            log.info("Chat model {} is not configured: THINKXECAI_GEMINI_API_KEY is not set", ChatModelNames.GEMINI_FLASH);
        }
    }

    /** @throws IllegalArgumentException if no chat client is registered under {@code model}, or it is not configured */
    public ChatClient get(String model) {
        ChatClient chatClient = model == null ? null : chatClients.get(model);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown chat model: " + model
                    + " (registered: " + String.join(", ", ChatModelNames.ALL) + ")");
        }
        if (notConfigured.contains(model)) {
            throw new IllegalArgumentException("Chat model " + model + " is not configured: its API key is not set");
        }
        return chatClient;
    }

}
