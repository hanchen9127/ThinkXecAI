# ADR 046: A run's memory ends with the message it answers

- **Status:** Accepted, 2026-10-21

## Context

Runs of one session take turns (ADR 042). A message sent while a run is going is stored before that run's later rows, so the next run read its history in stored order: `U1 U2 A1`. In the real Gemini runs (PR #16, 2026-10-21) every such run failed with `400 Requests ending with a model turn are not supported`; DeepSeek accepts it. Three more cases follow from the same order: `U2` stored between the earlier run's tool call and its result made the sanitizer drop that whole step; with two queued messages each run saw both questions; and an earlier run with more rows than `messageLength` (default 10; a five-step tool run writes 11) pushed `U2` out of the next run's window, so that run never saw its question.

## Options considered

- **The trigger message last**: the chat event carries the id of the user message that starts the run; its memory puts that message last and leaves newer user messages to their own runs.
- **The last user message last**, without changing the event: the same for one queued message; with two or more a run can answer the wrong one, and the window problem stays.
- **A fixed user line at the end** ("answer my latest message above") when memory ends with an assistant message: Gemini would accept it, but the model has to guess what is open, the line is not the user's, and the other three cases stay.

## Decision

The first option (chosen by the owner). `ChatEvent` carries `messageId` (the saved user message); the listener, scheduler, and runner pass it on. `ChatMessageMapper.selectTurnBySessionId` loads the latest `messageLength` messages up to and including the trigger, plus the newest `messageLength + 2 × 20 + 1` messages after it that are not user messages (round-4 review: unbounded, queued tool-heavy runs could overflow the model's context), so a long earlier run cannot push the trigger out. `RunTurn.endWith` then keeps the stored order except that the trigger moves to the end and user messages newer than it are left out; the sanitizer runs after that. `U1 U2 A1` becomes `U1 A1 U2`; `U1 A1(call) U2 T1 A1` becomes `U1 A1(call) T1 A1 U2`, keeping the tool step. The in-run window is sized from what was loaded when that is more than `messageLength`, so nothing is evicted. A trigger that is gone (deleted meanwhile) or not a user message of the session falls back to the latest `messageLength` rows in stored order.

Stored order, the chat-message API, and what the user sees do not change; only the memory of a run does.

## Trade-offs

- With three or more queued messages, older pairs keep their stored order (`U1 U2 A1 A2 U3` for the third run): the history reads slightly out of order, but each run ends with its own question and every question is answered once.
- A queued run's memory can be longer than `messageLength` by up to `messageLength + 41` rows of earlier output; with several tool-heavy runs ahead, the oldest of their output is cut (the sanitizer drops a step cut in half).
- One more mapper query; the run needs the message id, so a run without one (none today) would use the stored order.
- Tests: mapper (window up to the trigger plus the newest capped non-user rows after it; missing or foreign trigger), `RunTurnTest`, runner tests for a queued question, a question inside the earlier tool step, newer questions, a long earlier run, a deleted question, a trigger that is not a user message; the end-to-end test of two messages posted at once; the window test with more loaded than `messageLength`; external: a queued Gemini run.
