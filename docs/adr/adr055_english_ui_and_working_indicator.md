# ADR 055: An English UI, and a working indicator inferred on the client

- **Status:** Accepted, 2026-11-02 (amended 2026-11-03: a run ended by `terminate`)

## Context

Two frontend questions were open when Phase 13 started.

The starting design's interface was in Chinese, while the backend's messages (validation errors, run notices), the tool descriptions, the ADRs, and the reviews are in English, and the repository is public. Shown as they come, the backend's messages would mix two languages on one screen.

An agent run can take seconds to minutes: one model call may use 3 × 120 s + 8 s (ADR 039), and a run has up to 20 steps. The runtime emits only `AI_GENERATED_CONTENT`; `AI_THINKING`, `AI_EXECUTING`, and `AI_DONE` exist in the SSE contract but are never sent. Without a sign of progress, the wait after sending looks like nothing is happening.

## Options considered

Language:
- **English** (chosen): one language with the backend's messages; no new dependency.
- **Chinese**, as in the starting design: mixed with the backend's English errors.
- **Both, with a switch**: an i18n dependency and new scope.

Working indicator:
- **No indicator** (the spec as written): a blank wait of seconds to minutes.
- **Status events from the runtime**: exact, but a backend and contract change.
- **Inferred on the client** (chosen): from the messages the chat already has.

## Decision

The UI is English (Phase 13 D3).

The chat view shows that the agent is working (Phase 13 D4) when its newest message (by creation time) is a `user` message, an `assistant` message with tool calls, or a `tool` message; an `assistant` message without tool calls (an answer or a notice, ADR 043) ends it. So do the results of a reply with text that called `terminate`: the run ends there (ADR 045), while a `terminate` in a reply without text is refused and the run goes on. A tool result names the call it answers, so the reply is found by that id (the newest holding it, since ids come from the model). Amended 2026-11-03: the browser check against the running stack showed a run that answered with `terminate` still "working", its newest message being the `terminate` result. The same rule covers a sent message and a history loaded when the chat opens. It turns off 7 minutes after the newest message's own creation time, so each new message restarts the count and a run that died without a notice (a backend restart) does not keep it on. Counting from the message's time rather than from when the chat was opened was chosen by the owner on 2026-11-02: a run that died hours ago shows nothing when its chat is opened, instead of seven minutes of "working".

It is display only: the input stays enabled, since a session's runs queue (ADR 042). It shows as dots after the last step of the agent's card, with the number of steps so far (V1 element 9 A), or as a card of its own before the first step.

## Trade-offs

- The indicator is inferred: a run that ends without saving anything (none should, ADR 043) shows "working" until the 7 minutes pass.
- Counting from message times compares the server's clock with the browser's; both run on the same machine here, and a skew of minutes would only shorten or lengthen the fallback.
- English only; adding another language later needs an i18n layer over every string.
