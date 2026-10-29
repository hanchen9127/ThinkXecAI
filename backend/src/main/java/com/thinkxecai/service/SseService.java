package com.thinkxecai.service;

import com.thinkxecai.model.sse.SseMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

/**
 * One stream per chat session, delivered to best-effort (ISSUE-007): the messages are already saved, so a
 * missing or failed client is logged and dropped, and nothing here throws.
 */
public interface SseService {

    Duration TIMEOUT = Duration.ofMinutes(30);

    /**
     * Opens the session's stream, starting with an {@code init} event (data {@code connected}). A stream the
     * session already had first gets a {@code replaced} event and is then completed (ADR 048). Whether the
     * session exists is the caller's check.
     */
    SseEmitter connect(UUID chatSessionId);

    /** Sends the message as a {@code message} event to the session's stream, if it has one. */
    void send(UUID chatSessionId, SseMessage message);

    /** Completes and forgets the session's stream, if it has one. */
    void close(UUID chatSessionId);

}
