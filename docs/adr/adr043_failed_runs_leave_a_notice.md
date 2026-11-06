# ADR 043: A run that cannot finish leaves a notice in the session

- **Status:** Accepted, 2026-10-19

## Context

Runs happen in the background after the user's message is saved; nobody waits for them. In the starting design a failed run only logged an exception, so the user saw their question go unanswered with no explanation. Failures are real: the model can time out or return an error (ADR 039 bounds them), the agent can be deleted after the message was saved, a stored model name can be unknown, the executor queue can be full, and a run can use all 20 steps.

## Options considered

- **Log only** (the starting design).
- **Save a notice**: a plain assistant message with a generic reason.
- **A dedicated SSE status message** (`AI_DONE` and the other types exist in the contract but are not emitted; a v2 item).

## Decision

Save a notice (chosen by the owner). `AgentRunner` maps how a run ended to one English sentence and saves it as an `assistant` message without metadata, then publishes it like any generated message:

| Cause | Notice |
|---|---|
| model call failed (error, timeout, retries used up, no reply, or an empty reply: no text and no tool calls — saved, it would answer nothing and Gemini refuses a message without parts; owner, 2026-10-20) | "The agent could not finish this request: the model did not respond or returned an error." |
| agent deleted | "… the agent no longer exists." |
| model not registered | "… the agent's model is not available." |
| executor queue full | "… too many requests are being processed. Please try again." |
| 20 steps used | "The agent stopped after 20 steps without finishing the request." |
| anything else | "… an unexpected error occurred." |

Details go to the log only. If the session was deleted, nothing can be saved and the run stops silently (logged). A failing notice save is logged, never thrown. Tests: each cause above (the executor case with the scheduler, Phase 11 commit 5) ends with its notice as the last row; a deleted session saves nothing; removing the max-steps notice fails a test.

## Trade-offs

- The notice enters the next run's memory as ordinary assistant text; the model may refer to it.
- The reasons are deliberately coarse; the log has the cause.
