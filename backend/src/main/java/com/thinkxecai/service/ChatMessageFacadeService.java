package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.request.UpdateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;

import java.util.List;
import java.util.UUID;

public interface ChatMessageFacadeService {

    /** Oldest first. Unknown session → BizException 404. */
    List<ChatMessageVO> listChatMessagesBySession(UUID sessionId);

    /**
     * The public create-message path. Checked before anything is written: a user message needs
     * non-blank content (400), the session must exist (404), and a user message needs a session with
     * an agent (409). Refreshes the session's updated_at. Publishes a ChatEvent only for user messages.
     */
    ChatMessageVO createChatMessage(CreateChatMessageRequest request);

    /** PATCH semantics (content, metadata); never starts an agent run. Unknown id → BizException 404. */
    ChatMessageVO updateChatMessage(UUID chatMessageId, UpdateChatMessageRequest request);

    /** Unknown id → BizException 404. */
    void deleteChatMessage(UUID chatMessageId);

}
