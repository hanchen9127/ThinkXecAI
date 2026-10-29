package com.thinkxecai.service.impl;

import com.thinkxecai.model.sse.SseMessage;
import com.thinkxecai.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The emitter does its own locking, so the request thread's {@code init} and a run's messages can be sent
 * at the same time. Events sent before Spring MVC has taken the emitter are buffered and written first.
 * Logs name the session only, never what is sent.
 */
@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;

    public SseServiceImpl() {
        this(() -> new SseEmitter(TIMEOUT.toMillis()));
    }

    /** For tests that need emitters which fail. */
    SseServiceImpl(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    @Override
    public SseEmitter connect(UUID chatSessionId) {
        SseEmitter emitter = emitterFactory.get();
        // Each callback removes only its own emitter: a replaced one completes after its successor is registered.
        // Spring MVC wires them when it takes the emitter; if the client is gone by then they never run, and the
        // next send or connect removes the emitter instead.
        emitter.onCompletion(() -> emitters.remove(chatSessionId, emitter));
        emitter.onError(error -> emitters.remove(chatSessionId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(chatSessionId, emitter);
            // ends the stream normally, so the browser reconnects
            emitter.complete();
        });
        try {
            // before it is registered, so it is the first event even when a run is sending to the session
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE init failed for session {}: {}", chatSessionId, failure(e));
            emitter.completeWithError(e);
            return emitter;
        }
        SseEmitter previous = emitters.put(chatSessionId, emitter);
        if (previous != null) {
            replace(chatSessionId, previous);
        }
        return emitter;
    }

    /** The old tab closes on {@code replaced} instead of reconnecting and taking the stream back (ADR 048). */
    private void replace(UUID chatSessionId, SseEmitter previous) {
        try {
            previous.send(SseEmitter.event().name("replaced").data("replaced"));
            previous.complete();
        } catch (IOException | IllegalStateException e) {
            log.debug("Replaced SSE stream of session {} was already gone", chatSessionId);
            previous.completeWithError(e);
        }
    }

    @Override
    public void send(UUID chatSessionId, SseMessage message) {
        SseEmitter emitter = emitters.get(chatSessionId);
        if (emitter == null) {
            log.debug("No SSE client for session {}", chatSessionId);
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE send failed for session {}: {}", chatSessionId, failure(e));
            emitters.remove(chatSessionId, emitter);
            emitter.completeWithError(e);
        }
    }

    /**
     * Exception class names only, never messages, which can quote what was sent. Spring wraps anything but an
     * I/O error (a message that cannot be written as JSON, say) in IllegalStateException; the cause names it.
     */
    private static String failure(Exception e) {
        return e.getCause() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + " caused by " + e.getCause().getClass().getSimpleName();
    }

    @Override
    public void close(UUID chatSessionId) {
        SseEmitter emitter = emitters.remove(chatSessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    @Override
    public void close(UUID chatSessionId, SseEmitter emitter) {
        emitters.remove(chatSessionId, emitter);
        emitter.complete();
    }

}
