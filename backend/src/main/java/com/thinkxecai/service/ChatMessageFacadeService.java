package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;

import java.util.List;
import java.util.UUID;

public interface ChatMessageFacadeService {

    /** Oldest first. Unknown session → BizException 404. */
    List<ChatMessageVO> listChatMessagesBySession(UUID sessionId);

    /**
     * The public create-message path; always stores a user message without metadata (ADR 031).
     * Checked before anything is written: the session must exist (404) and have an agent (409);
     * non-blank content is checked by the request's validation. Refreshes the session's updated_at
     * and publishes a ChatEvent.
     */
    ChatMessageVO createChatMessage(CreateChatMessageRequest request);

    /** Unknown id → BizException 404. */
    void deleteChatMessage(UUID chatMessageId);

}
