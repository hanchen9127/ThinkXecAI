package com.thinkxecai.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness checks outside /api: raw text {@code ok}, not wrapped in ApiResponse, so they work
 * without any knowledge of the API envelope.
 */
@RestController
public class DiagnosticController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ok();
    }

    /** Checks that the frontend can reach the backend before it opens an SSE stream. */
    @GetMapping("/sse-test")
    public ResponseEntity<String> sseTest() {
        return ok();
    }

    /** A preset content type skips content negotiation: always text/plain "ok", whatever the Accept header asks for. */
    private static ResponseEntity<String> ok() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("ok");
    }

}
