# ADR 047: The SSE stream answers an unknown session with a real HTTP 404

- **Status:** Accepted, 2026-10-21 (implemented in PR #18, 2026-10-30; an exception to the HTTP-200 rule of ADR 002, like the Host check's 403 in ADR 033)

## Context

A browser opens a chat session's stream with `EventSource` on `GET /sse/connect/{chatSessionId}`. `EventSource` reconnects on its own after a stream that ended normally, about every 3 seconds, but gives up for good when the response has a status other than 200 or a content type other than `text/event-stream`. The starting design accepted any HTTP method on the path and never checked that the session existed: a wrong or deleted id got a stream that never received anything, was held for 30 minutes, and was then reconnected. Every other endpoint of the API answers errors with HTTP 200 and the error in the envelope's `code` (ADR 002).

## Options considered

- **Connect anyway** (the starting design): the client cannot tell a wrong id from a quiet session, and holds a connection for nothing.
- **HTTP 200 with `code: 404`**, as the other endpoints do: `EventSource` would also stop (on the JSON content type), but the reason is visible only in the body, which `EventSource` does not expose, and content negotiation against `Accept: text/event-stream` can turn the JSON error into a 406 or 500.
- **A real HTTP status with the envelope as JSON.**

## Decision

The stream opens only for an existing session. An unknown session gets a real HTTP 404, an id that is not a UUID a real HTTP 400, and a failed lookup a real HTTP 500 without details; each body is the usual `ApiResponse` envelope as `application/json`. `GET` only.

Implementation: `SseController` has its own `@ExceptionHandler`, which takes precedence over `GlobalExceptionHandler` for this controller only, and returns a `ResponseEntity` with the content type preset to JSON, which skips content negotiation. Without either, the error could not be written for an `EventSource`'s `Accept` header (both checked by removing them in tests). Once a stream has started, a later error (the client went away) finds the response committed and nothing more is written. Requests Spring MVC refuses before the controller, such as another HTTP method, keep the usual envelope at HTTP 200.

## Trade-offs

- The SSE path is a second exception to "errors are HTTP 200", so the frontend must handle its errors on the status, not on `code`.
- `/sse` is outside `/api` and not in the OpenAPI document, so the frontend's types for it stay hand-written (ADR 021) and are checked by a backend test that reads `ui/src/api/api.ts`.
