package com.thinkxecai.agent.memory;

import com.thinkxecai.agent.AgentLimits;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-run memory of one agent run (ISSUE-003 C). {@code messageLength} limits only what is loaded;
 * the window also has room for the system message and for every message the run can add (one
 * assistant and one tool-result message per step), so nothing is evicted during a run. The starting
 * design sized the window as {@code messageLength}: after a few tool steps the user's question was
 * evicted and the memory could start with a tool result.
 */
public final class AgentMemoryWindow {

    private AgentMemoryWindow() {
    }

    public static int size(int messageLength) {
        return 1 + messageLength + 2 * AgentLimits.MAX_STEPS;
    }

    /** A memory holding the system message first, then the loaded messages, under {@code conversationId}. */
    public static ChatMemory create(String conversationId, SystemMessage systemMessage, List<Message> loaded,
                                    int messageLength) {
        // A queued run loads more than messageLength (the earlier run's output, ADR 046): room for all of it.
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(size(Math.max(messageLength, loaded.size())))
                .build();
        List<Message> initial = new ArrayList<>(loaded.size() + 1);
        initial.add(systemMessage);
        initial.addAll(loaded);
        memory.add(conversationId, initial);
        return memory;
    }

}
