package com.thinkxecai.agent;

import com.thinkxecai.model.vo.ChatMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Until SSE exists (Phase 12) generated messages are only saved; this records that they were produced. */
@Slf4j
@Component
public class LoggingGeneratedMessagePublisher implements GeneratedMessagePublisher {

    @Override
    public void publish(UUID sessionId, ChatMessageVO message) {
        log.debug("Generated {} message {} in session {}", message.getRole(), message.getId(), sessionId);
    }

}
