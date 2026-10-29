package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.exception.GlobalExceptionHandler;
import com.thinkxecai.service.ChatSessionFacadeService;
import com.thinkxecai.service.SseService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * A chat session's stream, outside /api and so not in the OpenAPI document (ADR 021). Unlike /api, a stream
 * that cannot be opened (unknown session, malformed id, failed lookup) gets a real HTTP status with the envelope
 * as JSON: a browser EventSource stops on a status other than 200 instead of reconnecting to a session that does
 * not exist (ADR 047). Requests Spring MVC refuses before this controller (another method) get the usual envelope.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final ChatSessionFacadeService chatSessionFacadeService;
    private final SseService sseService;

    @GetMapping("/sse/connect/{chatSessionId}")
    public SseEmitter connect(@PathVariable UUID chatSessionId) {
        SseEmitter emitter = sseService.connect(chatSessionId);
        // Checked after the stream is registered: a delete committing meanwhile either closes the stream
        // after its commit or is seen here (ADR 049). Checked first, the stream of a session deleted between
        // the check and connect would stay open until its timeout.
        try {
            // BizException 404 when the session does not exist
            chatSessionFacadeService.getChatSession(chatSessionId);
        } catch (RuntimeException e) {
            sseService.close(chatSessionId);
            throw e;
        }
        return emitter;
    }

    /** Takes precedence over GlobalExceptionHandler for this controller only. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            // The stream had started and then failed (the client went away): there is nothing left to write.
            log.debug("SSE stream ended with {}", ex.getClass().getSimpleName());
            return null;
        }
        if (ex instanceof BizException biz && Integer.valueOf(404).equals(biz.getCode())) {
            log.warn("SSE connect refused: {}", biz.getMessage());
            return error(HttpStatus.NOT_FOUND, biz.getMessage());
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            log.warn("SSE connect refused: {} is not a UUID", mismatch.getName());
            return error(HttpStatus.BAD_REQUEST, mismatch.getName() + ": must be a UUID");
        }
        log.error("SSE connect failed", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE);
    }

    /** A preset content type skips content negotiation: JSON although an EventSource accepts only text/event-stream. */
    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(status.value(), message));
    }

}
