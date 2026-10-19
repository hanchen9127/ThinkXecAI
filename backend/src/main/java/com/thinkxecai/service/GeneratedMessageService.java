package com.thinkxecai.service;

import com.thinkxecai.exception.ChatSessionGoneException;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.vo.ChatMessageVO;

import java.util.List;
import java.util.UUID;

/**
 * Saves the messages an agent run generates: assistant and tool messages (ADR 031: the public API
 * creates only user messages). Not exposed over HTTP.
 */
public interface GeneratedMessageService {

    /**
     * Saves the messages in one transaction, in order, with the session locked like a user message
     * (ADR 037) and its {@code updated_at} refreshed (ADR 018). Only {@code assistant} and {@code tool}
     * messages are accepted; their {@code sessionId} is ignored.
     *
     * @return the saved messages as stored, with database-generated ids and timestamps
     * @throws ChatSessionGoneException if the session no longer exists; nothing is saved
     */
    List<ChatMessageVO> save(UUID sessionId, List<ChatMessageDTO> messages);

}
