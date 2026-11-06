# ADR 044: Generated messages go to a publisher; SSE implements it later

- **Status:** Accepted, 2026-10-19

## Context

The roadmap had Phase 11 push generated messages over SSE, but the SSE service is Phase 12. ISSUE-007 showed that in the starting design a failed SSE send threw inside the agent loop and ended the run, so closing the browser tab mid-run stopped the agent.

## Options considered

- **An interface now, SSE in Phase 12.**
- **Phases 11 and 12 in one pull request.**
- **A minimal SSE in Phase 11**, completed in Phase 12.

## Decision

The first option (chosen by the owner). After each message is saved, the runtime passes its `ChatMessageVO` to `GeneratedMessagePublisher.publish(sessionId, message)`. Phase 11's only implementation logs at debug level. Every call is wrapped so that anything the publisher throws is logged and the run goes on: delivery can never stop or fail a run (the runtime half of ISSUE-007). Roadmap Phase 11 step 13 and the acceptance criteria about the SSE client moved to Phase 12, which implements the interface with SSE. Tests: a publisher that throws on every call; the run still saves every message.

## Trade-offs

- Until Phase 12, generated messages are visible only by reloading the session's messages.
- Delivery happens after each save, outside its transaction; a client can miss a message, which Phase 13 covers by reloading on connect (ISSUE-007).
