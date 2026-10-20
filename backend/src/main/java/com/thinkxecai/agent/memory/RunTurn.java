package com.thinkxecai.agent.memory;

import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Puts the message a run answers last (ADR 046). Runs of a session take turns (ADR 042), so a message sent
 * during a run is stored before that run's later rows: the next run would read {@code U1 U2 A1}, ending
 * with the model's turn, which Gemini refuses, and could find {@code U2} between a tool call and its result.
 * Here the run's own message moves behind everything stored before it ran, and newer user messages are
 * left to their own runs. Stored order and what the user sees do not change.
 */
public final class RunTurn {

    private RunTurn() {
    }

    /**
     * @param stored  the session's recent rows, oldest first
     * @param trigger the user message this run answers, or null to keep {@code stored} as it is (it was
     *                deleted, or the run has none); it may be older than {@code stored}
     */
    public static List<ChatMessageDTO> endWith(List<ChatMessageDTO> stored, ChatMessageDTO trigger) {
        if (trigger == null) {
            return stored;
        }
        UUID triggerId = trigger.getId();
        int position = indexOf(stored, triggerId);
        List<ChatMessageDTO> turn = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            ChatMessageDTO message = stored.get(i);
            boolean newerUserMessage = i > position && message.getRole() == ChatMessageRole.USER;
            if (i != position && !newerUserMessage) {
                turn.add(message);
            }
        }
        turn.add(trigger);
        return turn;
    }

    /** -1 when the trigger is older than every stored row: then every stored user message is newer. */
    private static int indexOf(List<ChatMessageDTO> stored, UUID id) {
        for (int i = 0; i < stored.size(); i++) {
            if (id.equals(stored.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

}
