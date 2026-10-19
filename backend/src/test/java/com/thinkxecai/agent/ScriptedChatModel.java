package com.thinkxecai.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * A chat model that answers from a script and records every prompt it gets, so runtime tests need no
 * real model. A script step either returns a reply or throws; running out of steps is a test error.
 */
class ScriptedChatModel implements ChatModel {

    private final Deque<Supplier<ChatResponse>> script = new ArrayDeque<>();
    final List<Prompt> prompts = new ArrayList<>();

    ScriptedChatModel then(Supplier<ChatResponse> step) {
        script.add(step);
        return this;
    }

    ScriptedChatModel thenReply(String text) {
        return then(() -> reply(text));
    }

    ScriptedChatModel thenCall(String text, AssistantMessage.ToolCall... calls) {
        return then(() -> calls(text, calls));
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Supplier<ChatResponse> step = script.poll();
        if (step == null) {
            throw new IllegalStateException("The script has no reply for call " + prompts.size());
        }
        return step.get();
    }

    static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }

    static ChatResponse calls(String text, AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content(text).toolCalls(Arrays.asList(calls)).build())));
    }

    static AssistantMessage.ToolCall call(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

}
