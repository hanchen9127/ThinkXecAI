# ADR 042: Runs of one chat session execute one at a time

- **Status:** Accepted, 2026-10-19

## Context

A user message starts an agent run after its transaction commits (ISSUE-004). The starting design ran every run as soon as its event arrived (`@Async @EventListener`, no lock), so two messages sent quickly to one session ran concurrently. Each run saves its rows as it goes, so the rows interleave: `U1 U2 A1(call x) A2(call y) T1 T2 …`. DeepSeek and Gemini both require a tool result to follow its call directly; the next run's history is rejected with 400, and the session stays broken until those rows are deleted.

## Options considered

- **Serialize runs per session in the process**: a session's runs wait for each other, different sessions run in parallel.
- **Allow concurrency and repair the order when memory is sanitized**: tool results moved back behind their calls; both runs may still answer both questions.
- **Refuse a new message with 409 while a run is active**: an API change, and the frontend would need to retry.

## Decision

The first option (chosen by the owner). `AgentRunScheduler` keeps, per session id, the tail of a chain of runs (`ConcurrentHashMap<UUID, CompletableFuture<Void>>`). `submit` puts a new tail and chains the run on the previous one with `thenRunAsync(run, asyncEventExecutor)`, so no thread blocks while waiting. A run loads its memory only when it starts, after the previous run of its session has saved everything, so it sees the earlier answer and its own question. The entry is removed when its run is the last one queued.

The chat event listener (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`, no `@Async`) only calls `submit`. The executor keeps `AbortPolicy`: when its queue is full the chained stage completes with a `RejectedExecutionException`, and the scheduler saves the "too many requests" notice (ADR 043) before the session's turn ends, so a later run sees the notice. That notice can be saved on the request thread inside the commit callback, where the committed transaction is still bound to the thread; that transaction is suspended (`NOT_SUPPORTED`), so the save runs in a transaction of its own and is committed when `submit` returns; a joined transaction would leave the notice to the connection's later reset, and a failed save (deleted session) would surface as an `UnexpectedRollbackException`. Only an `Error` can end a run with another failure (`AgentRunner` catches every exception); it is logged and the next run still starts.

In-process only: the backend runs as one local instance (ADR 033). Tests: per-session order and cross-session overlap on a real thread pool, rejections immediately and while waiting, the map emptied when idle, a run that throws not holding up the session; end to end, two messages posted at once answered one after the other, and a rejection inside a commit callback whose notice is visible to another connection when `submit` returns.

## Trade-offs

- A second message waits for the whole first run (up to 20 steps).
- A message sent while a run is going is saved before that run's answer (`U1 U2 A1`); the next run's memory puts its own message last instead (ADR 046), since Gemini refuses a request that ends with the model's turn.
- Waiting runs live only in memory: a restart drops them silently (the user messages stay; sending another message starts a run that sees them).
- With several backend instances, runs of one session could still overlap; that would need a database lock or a queue.
