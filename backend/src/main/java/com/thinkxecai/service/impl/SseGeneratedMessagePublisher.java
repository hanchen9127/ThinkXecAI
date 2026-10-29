package com.thinkxecai.service.impl;

import com.thinkxecai.agent.GeneratedMessagePublisher;
import com.thinkxecai.model.sse.SseMessage;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Pushes each message a run has saved to the session's stream, if a browser has one open (ADR 044). */
@Component
@RequiredArgsConstructor
public class SseGeneratedMessagePublisher implements GeneratedMessagePublisher {

    private final SseService sseService;

    @Override
    public void publish(UUID sessionId, ChatMessageVO message) {
        sseService.send(sessionId, SseMessage.generated(message));
    }

}
