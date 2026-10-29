package com.thinkxecai.agent;

import com.thinkxecai.model.vo.ChatMessageVO;

import java.util.UUID;

/**
 * Delivers each message an agent run has saved to whoever is watching the session (ADR 044), implemented
 * with SSE by {@code SseGeneratedMessagePublisher}. Delivery is best effort: the runtime catches anything
 * thrown here, so a failed delivery never stops a run (ISSUE-007).
 */
public interface GeneratedMessagePublisher {

    void publish(UUID sessionId, ChatMessageVO message);

}
