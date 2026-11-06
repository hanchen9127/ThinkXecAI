# ADR 060: The user can stop a session's runs between steps

- **Status:** Accepted, 2026-11-04 (adds to the mission's feature scope; amends 042, 043)

## Context

A run could not be stopped: once a message was sent, the agent took up to 20 steps, including tools with effects such as `sendEmail`, and further messages queued behind it (ADR 042). The design review named this the main gap in user control. The owner chose to add stopping, designed together with the interface (round 3).

## Options considered

- **When it takes effect:** between steps only; or also by aborting the model call in progress, which would need proof that both chat clients abort cleanly with their retries (ADR 039).
- **Which runs:** the running one and those queued behind it; or only the running one, after which the next queued run starts at once.
- **What is stored:** a notice per stopped run; or nothing, which would need a new SSE event and leave a reload unable to tell a stopped run from a running one.
- **Retry:** the frontend sends the question again; or a backend endpoint re-runs the original message, whose memory would then hold the stopped run's steps (ADR 046).

## Decision

`POST /api/chat-sessions/{chatSessionId}/stop` (no body, `data` null; code 404 for an unknown session; 200 when nothing is running) stops the session's running run and every run queued behind it. `AgentRunScheduler` gives the runs queued in a session one shared stop flag; a stop sets it and starts a new one for runs submitted later, and the runtime checks it before each Think and before each Execute. A model call or tool already running finishes first. A stopped run ends with `Outcome.STOPPED` and saves one assistant notice, "Stopped.", published like ADR 043's notices; a queued run that was stopped saves its notice without calling the model. Steps saved before the stop stay; a Think whose tool calls were not executed stays too, and the memory sanitizer already drops calls without results from the next run (ISSUE-003 B). No new SSE type: ADR 055's working indicator ends at the notice. Retry is the frontend sending the same text as a new user message. State is in memory, like the scheduler (one local instance).

In the UI, while a run works and the composer is empty, Send becomes Stop; typing turns it back into Send, so a message can still be queued. After a click it reads "Stopping…" until the notice arrives. "Stopped." and the other notices end with Try again.

**Amended 2026-11-06 (owner).** Only the latest stopped or failed run has Try again, on its last notice. Stored messages run question, question, then both notices when a queued run is stopped, so all the notices sit after the last question and the first question could not be reached; both buttons sent the last one. Tying each notice to its question was not chosen: it needs the notice to record its message, for a rare case.

**Amended again 2026-11-06 (owner, PR #20 review).** Try again shows only while the notice is the last thing in the chat and every question before it has its reply. A button that stayed after use could send a question twice, and a message typed during Stopping… is stored before the notice, so the question above that notice could be one the agent went on to answer. Replies are counted against questions over the whole chat, because a message sent during a run splits it in two in the history; a run that died without a reply stays owed, so that chat offers no Try again afterwards, the safe side. A click while the first is being sent does nothing.

The stop endpoint now answers `data` `true` when the session had a run to stop and `false` when not, in place of `null`. The UI infers that a run is working from the last message, so after a backend restart Stop got a 200, no notice ever came, and the button stayed on Stopping… for up to 7 minutes. On `false` the working state ends at once. The answer is a plain boolean because `ApiResponse` has no per-endpoint wrappers. Reopening such a chat within those 7 minutes still shows it working, until Stop is used.

## Trade-offs

- Stop can take as long as the step in progress (a model call can take minutes, ADR 039).
- A stop races with a run that is just finishing; the run may answer before it sees the stop, and then nothing is stopped.
- A retry shows the question twice in the history.
