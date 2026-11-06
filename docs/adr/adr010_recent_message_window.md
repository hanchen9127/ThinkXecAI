# ADR 010: Agent memory loads the latest messages, not the first ones

- **Status:** Accepted, 2026-10-04

## Context

Before each run, the agent reloads part of the conversation from the database as its memory, limited to a configurable number of messages (10 by default). The starting design sorted the session's messages oldest-first and took the first N. Once a conversation grew past N messages, the agent kept receiving the same opening messages and never saw the question the user had just asked.

## Options considered

- **Keep the first N messages**, as in the starting design: simple, but wrong for any long conversation.
- **Load the whole conversation** and cut it in Java: correct, but loads ever more data as sessions grow.
- **Ask the database for the latest N, then put them back in chronological order**, with one query that has a small subquery.

## Decision

Load the latest N messages in chronological order, in a single query: the inner part picks the newest N, the outer part re-sorts them oldest-first, which is the order a language model expects. A test with 15 messages and a limit of 10 checks that messages 6 to 15 come back in order; putting the original "first N" query back makes that test fail.

## Trade-offs

- The query is slightly more complex than a plain sort with a limit.
- Correct order depends on message timestamps being distinct, which ADR 005 guarantees (`clock_timestamp()`).
- A window cut can still split a tool call from its result. That is handled when memory is rebuilt in the agent runtime, not in this query.
