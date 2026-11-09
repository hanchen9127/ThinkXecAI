# ADR 005: The database sets all timestamps

- **Status:** Accepted, 2026-10-03

## Context

Every table has `created_at` and `updated_at`. The starting design set them in Java *and* gave the columns database defaults, so two clocks wrote the same data. Separately, chat messages are listed by `created_at`, but PostgreSQL's `now()` returns the same value for a whole transaction. An assistant message and its tool results, saved together, would then share a timestamp and could come back in the wrong order.

## Options considered

- **Java sets timestamps** (application server clock).
- **The database sets timestamps** (column defaults on insert, `updated_at = now()` on update).
- For message order: **keep `now()`**, **add a sequence number column**, or **use `clock_timestamp()`**, which advances with every statement.

## Decision

The database is the only source of timestamps. Java never sets them; after an insert, the generated values are read back in the same statement. Chat messages use `clock_timestamp()` so messages saved in one transaction keep their insert order. Verified: 5 messages inserted in one transaction got 5 distinct timestamps, in order.

## Trade-offs

- An object only has its timestamps after it has been saved. Callers must use what the insert returns, which the data layer does by design.
- A tie-breaker column would make ordering independent of timestamps entirely; `clock_timestamp()` was chosen because it needs no schema or query changes.
