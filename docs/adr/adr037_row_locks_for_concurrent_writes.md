# ADR 037: Concurrent writes take row locks

- **Status:** Accepted, 2026-10-17

## Context

Updates read a row, merge the request into it in memory, and write every field back. At PostgreSQL's default isolation level two updates at the same time could therefore undo each other: an agent rename running next to a description change wrote the old description back. A review of the merged code also found that checks done before writing could be outdated by the time of the write. An agent update could add a knowledge base that was being deleted, leaving an id that ADR 014 promises never to keep. A message, session, or document could be created for a row that was deleted in between, and the request failed with a foreign-key error (500) instead of the usual 404 or 400. One user rarely does two things at once, but a double click or two open tabs can.

## Options considered

- **Record the risk:** the effects are rare and small for a single user.
- **Serializable isolation:** PostgreSQL detects the conflicts, but the losing request fails and must be retried, which the API does not do.
- **Row locks where a request reads before it writes.**

## Decision

The third option. Agent and knowledge-base updates read the row `FOR UPDATE`, so a concurrent update waits and then merges into the newer row; document and session updates change a single field, where the last write winning loses nothing. A request that depends on another row locks it while checking it: `FOR SHARE` on the agent for a new session, on the knowledge base for a new document or upload (an upload checks again inside its transaction, after the slow embedding call), and on each knowledge base an agent lists. A new message locks its session `FOR NO KEY UPDATE`, the same lock the session's activity time update takes anyway; a weaker share lock would let two messages to one session deadlock on that update. An agent update locks its knowledge bases before the agent row, the order a knowledge-base delete uses (its row, then the agents), so those two cannot deadlock either. A request that waited for a delete then gets its normal 404 or 400. Tests hold one transaction open, start the competing request on another thread, check that it waits, and check the result after the commit; each of the races failed on the old code. A later race pins the session lock's strength (an agent delete clearing the session's agent must make a new message wait and get 409). Deadlocks themselves are not tested: the harness runs the first request completely before the second starts, so a wrong lock order would only be caught in review.

## Trade-offs

- A request can wait for another one to finish. Every transaction involved is short (the slow embedding call happens before the upload's transaction), so the wait is milliseconds.
- New write paths must follow the same lock order; the tests cover the current ones.
- Two user messages sent at the same time are both saved in turn; whether that should start two agent runs on one session is decided with the agent runtime (Phase 11).
