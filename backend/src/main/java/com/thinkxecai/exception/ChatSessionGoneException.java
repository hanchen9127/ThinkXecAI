package com.thinkxecai.exception;

import java.util.UUID;

/**
 * The chat session an agent run writes to no longer exists (deleted while the run was going). Nothing
 * can be saved for it any more, so the run stops; it is not a client error.
 */
public class ChatSessionGoneException extends RuntimeException {

    public ChatSessionGoneException(UUID sessionId) {
        super("Chat session no longer exists: " + sessionId);
    }

}
