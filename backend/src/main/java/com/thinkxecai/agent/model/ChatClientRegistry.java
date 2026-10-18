package com.thinkxecai.agent.model;

import com.thinkxecai.model.ChatModelNames;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the {@link ChatClient} for an agent's {@code model}. The clients are the beans named after
 * {@link ChatModelNames}; startup fails if one is missing or another is registered, so every name an
 * agent can be saved with resolves (ADR 040). An unknown name (only possible in a row written outside
 * the API) is an {@link IllegalArgumentException}, which the agent runtime reports as a failed run.
 */
@Component
public class ChatClientRegistry {

    private final Map<String, ChatClient> chatClients;

    public ChatClientRegistry(Map<String, ChatClient> chatClients) {
        List<String> missing = new ArrayList<>(ChatModelNames.ALL);
        missing.removeAll(chatClients.keySet());
        List<String> extra = new ArrayList<>(chatClients.keySet());
        extra.removeAll(ChatModelNames.ALL);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalStateException("Chat clients must be exactly " + ChatModelNames.ALL
                    + "; missing: " + missing + ", unexpected: " + extra);
        }
        this.chatClients = Map.copyOf(chatClients);
    }

    /** @throws IllegalArgumentException if no chat client is registered under {@code model} */
    public ChatClient get(String model) {
        ChatClient chatClient = model == null ? null : chatClients.get(model);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown chat model: " + model
                    + " (registered: " + String.join(", ", ChatModelNames.ALL) + ")");
        }
        return chatClient;
    }

}
