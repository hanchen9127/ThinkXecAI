package com.thinkxecai.agent.memory;

import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The run's own message last, newer questions left to their runs (ADR 046). */
class RunTurnTest {

    private static ChatMessageDTO row(ChatMessageRole role, String text) {
        return ChatMessageDTO.builder().id(UUID.randomUUID()).role(role).content(text).build();
    }

    private static List<String> texts(List<ChatMessageDTO> rows) {
        return rows.stream().map(ChatMessageDTO::getContent).toList();
    }

    @Test
    void theTriggerMovesBehindTheRowsStoredAfterIt() {
        ChatMessageDTO q2 = row(ChatMessageRole.USER, "q2");
        List<ChatMessageDTO> stored = List.of(row(ChatMessageRole.USER, "q1"), q2, row(ChatMessageRole.USER, "q3"),
                row(ChatMessageRole.ASSISTANT, "a1"), row(ChatMessageRole.TOOL, "t1"));

        assertThat(texts(RunTurn.endWith(stored, q2))).containsExactly("q1", "a1", "t1", "q2");
    }

    @Test
    void aTriggerThatIsAlreadyLastChangesNothing() {
        ChatMessageDTO q2 = row(ChatMessageRole.USER, "q2");
        List<ChatMessageDTO> stored = List.of(row(ChatMessageRole.USER, "q1"), row(ChatMessageRole.ASSISTANT, "a1"), q2);

        assertThat(RunTurn.endWith(stored, q2)).containsExactlyElementsOf(stored);
    }

    @Test
    void aTriggerOlderThanTheRowsLeavesOutEveryStoredQuestion() {
        ChatMessageDTO q1 = row(ChatMessageRole.USER, "q1");
        List<ChatMessageDTO> stored = List.of(row(ChatMessageRole.ASSISTANT, "a1"), row(ChatMessageRole.USER, "q2"));

        assertThat(texts(RunTurn.endWith(stored, q1))).containsExactly("a1", "q1");
    }

    @Test
    void withoutATriggerTheStoredOrderStays() {
        List<ChatMessageDTO> stored = List.of(row(ChatMessageRole.USER, "q1"), row(ChatMessageRole.ASSISTANT, "a1"));

        assertThat(RunTurn.endWith(stored, null)).isSameAs(stored);
    }

}
