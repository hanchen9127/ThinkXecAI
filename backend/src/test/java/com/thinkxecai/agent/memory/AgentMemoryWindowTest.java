package com.thinkxecai.agent.memory;

import com.thinkxecai.agent.AgentLimits;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A full run never evicts anything from the in-run memory, while a window of {@code messageLength}
 * (the starting design) loses the user's question after a few tool steps (ISSUE-003 C).
 */
class AgentMemoryWindowTest {

    private static final String SESSION = "session";
    private static final int MESSAGE_LENGTH = 10;
    private static final UserMessage QUESTION = new UserMessage("What is the weather in Sydney tomorrow?");
    private static final SystemMessage SYSTEM = new SystemMessage("You are a helpful agent.");

    /** {@code MESSAGE_LENGTH} loaded messages, the last one being the question that started this run. */
    private static List<Message> loadedHistory() {
        List<Message> history = new ArrayList<>();
        while (history.size() < MESSAGE_LENGTH - 1) {
            int i = history.size();
            history.add(i % 2 == 0 ? new UserMessage("earlier question " + i)
                    : AssistantMessage.builder().content("earlier answer " + i).build());
        }
        history.add(QUESTION);
        return history;
    }

    /** One Think-Execute step that called a tool: the assistant's call and its result. */
    private static void addToolStep(ChatMemory memory, int step) {
        String id = "call_" + step;
        memory.add(SESSION, AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", "weather", "{}")))
                .build());
        memory.add(SESSION, ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, "weather", "sunny")))
                .build());
    }

    @Test
    void theStartingDesignsWindowEvictsTheQuestionAfterAFewToolSteps() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(MESSAGE_LENGTH).build();
        memory.add(SESSION, loadedHistory());
        memory.add(SESSION, SYSTEM);

        for (int step = 1; step <= 5; step++) {
            addToolStep(memory, step);
        }

        assertThat(memory.get(SESSION)).doesNotContain(QUESTION);
    }

    @Test
    void theWindowHoldsTheSystemMessageTheLoadedMessagesAndAFullRun() {
        assertThat(AgentMemoryWindow.size(MESSAGE_LENGTH)).isEqualTo(1 + MESSAGE_LENGTH + 2 * AgentLimits.MAX_STEPS);
    }

    @Test
    void aFullRunEvictsNothingAndTheSystemMessageStaysFirst() {
        ChatMemory memory = AgentMemoryWindow.create(SESSION, SYSTEM, loadedHistory(), MESSAGE_LENGTH);

        for (int step = 1; step <= AgentLimits.MAX_STEPS; step++) {
            addToolStep(memory, step);
        }

        List<Message> messages = memory.get(SESSION);
        assertThat(loadedHistory()).hasSize(MESSAGE_LENGTH);
        assertThat(messages).hasSize(1 + MESSAGE_LENGTH + 2 * AgentLimits.MAX_STEPS);
        assertThat(messages.get(0)).isSameAs(SYSTEM);
        assertThat(messages.subList(1, 1 + MESSAGE_LENGTH)).containsExactlyElementsOf(loadedHistory());
    }

    @Test
    void aQueuedRunThatLoadedMoreThanMessageLengthStillEvictsNothing() {
        // ADR 046: the earlier run's output comes on top of messageLength
        List<Message> loaded = new ArrayList<>(loadedHistory().subList(0, MESSAGE_LENGTH - 1));
        for (int i = 0; i < 15; i++) {
            loaded.add(AssistantMessage.builder().content("earlier run " + i).build());
        }
        loaded.add(QUESTION);
        ChatMemory memory = AgentMemoryWindow.create(SESSION, SYSTEM, loaded, MESSAGE_LENGTH);

        for (int step = 1; step <= AgentLimits.MAX_STEPS; step++) {
            addToolStep(memory, step);
        }

        List<Message> messages = memory.get(SESSION);
        assertThat(messages).hasSize(1 + loaded.size() + 2 * AgentLimits.MAX_STEPS);
        assertThat(messages.subList(1, 1 + loaded.size())).containsExactlyElementsOf(loaded);
    }

}
