# ADR 049: Deleting a session closes its SSE stream after the delete commits

- **Status:** Accepted, 2026-10-21 (implemented in PR #18, 2026-10-30; the connect order revised in its review)

## Context

A deleted chat session can still have an open stream. Left alone, it stays open until its 30-minute timeout or until the page closes it, holding a connection for a session that no longer exists. Closing it inside the deleting transaction would be wrong if the transaction then rolls back.

## Options considered

- **Leave it** until the timeout or the page.
- **Close it in the delete**, before the commit: a rollback would leave the session existing and its stream closed.
- **Close it after the commit.**

## Decision

`ChatSessionFacadeServiceImpl.deleteChatSession` registers a transaction synchronization whose `afterCommit` calls `SseService.close(id)`, which completes the emitter without an event and never throws. A rollback leaves the stream open. The browser's `EventSource` sees a normal end and reconnects, gets HTTP 404 (ADR 047), and stops.

A delete can commit while a connect is in progress. `SseController.connect` therefore checks the session, registers the stream, and checks again: a delete that commits before the second check is seen by it, one that commits after finds the registered stream in its after-commit close. A failed second check closes only the request's own emitter (`SseService.close(id, emitter)`, compare-and-remove), so a stream another request registered meanwhile is left alone. The first check keeps an unknown session or a failed lookup from registering anything (and from replacing another tab's stream, ADR 048). Both refinements came from the pull request review: with a single check before registering, a delete in between left the stream open until its timeout; with a single check after, a failed lookup cost another tab its stream.

## Trade-offs

- Every connect makes two session lookups.
- When the first check passes and the second fails (a delete in between, or a database error between two lookups milliseconds apart), the stream this request replaced has already had `replaced` and is lost; for a delete it had to end anyway.
