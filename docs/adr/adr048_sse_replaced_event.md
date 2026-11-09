# ADR 048: A replaced SSE stream gets a `replaced` event before it ends

- **Status:** Accepted, 2026-10-21 (implemented in PR #18, 2026-10-30; amends ISSUE-007's emitter-replacement rule)

## Context

The mission keeps one stream per chat session. When a second tab (or a reload, or React's double-mounted effects in development) connects to the same session, the new stream replaces the old one. The starting design neither completed the old emitter nor guarded its callbacks, so the old emitter's later timeout or error removed the new one (ISSUE-007). Completing the old emitter fixes that, but its `EventSource` sees a normal end and reconnects about 3 seconds later, replacing the new tab's stream in turn: two tabs on one chat would take the stream from each other every few seconds, each time reloading the history.

## Options considered

- **Several streams per session:** the friendliest for tabs, but it changes "one connection per chat session" and needs a cap.
- **Complete the old stream silently** (the spec unchanged): the two tabs flap.
- **Tell the old stream why it ends.**

## Decision

Connecting again sends the previous emitter an event named `replaced` (data `replaced`), then completes it. The frontend (Phase 13) closes its `EventSource` on `replaced` and does not reconnect. Completion, timeout, and error callbacks remove only the emitter they belong to (`ConcurrentHashMap.remove(id, emitter)`), so a replaced emitter that ends after its successor was registered leaves the successor in place. A replaced stream that is already gone is completed with its error.

Related implementation choices (PR #18): `init` is sent before the new emitter is registered, so it is the first event even while a run is sending; a stream is registered only after the session check passes (ADR 047), so an unknown session or a failed lookup sends no `replaced` to another tab; on timeout the emitter is completed, so the stream ends normally and the browser reconnects.

## Trade-offs

- The tab that lost the stream stays open without live updates until the user reloads it; it is not told which tab took over.
- `replaced` is one more event type the hand-written frontend contract must know about.
