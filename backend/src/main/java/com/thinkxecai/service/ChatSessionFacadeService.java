package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;

import java.util.List;
import java.util.UUID;

public interface ChatSessionFacadeService {

    /** Most recently updated first. */
    List<ChatSessionVO> listChatSessions();

    /** Unknown id → BizException 404. */
    ChatSessionVO getChatSession(UUID chatSessionId);

    /** Most recently updated first. Unknown agent → BizException 404. */
    List<ChatSessionVO> listChatSessionsByAgent(UUID agentId);

    /** agentId is optional; an unknown agent → BizException 400. */
    ChatSessionVO createChatSession(CreateChatSessionRequest request);

    /** Changes the title only; the agent is fixed at creation. Unknown id → BizException 404. */
    ChatSessionVO updateChatSession(UUID chatSessionId, UpdateChatSessionRequest request);

    /**
     * Stops the session's running run and every run queued behind it (ADR 060). They stop
     * between steps, so the step in progress finishes first. Runs submitted later run as usual.
     * Unknown id → BizException 404.
     *
     * @return whether the session had a run to stop; if not, no notice will follow
     */
    boolean stopChatSession(UUID chatSessionId);

    /** Deletes the session and its messages (foreign key cascade). Unknown id → BizException 404. */
    void deleteChatSession(UUID chatSessionId);

}
