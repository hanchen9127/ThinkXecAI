# ADR 034: Database sessions run in UTC

- **Status:** Accepted, 2026-10-17

## Context

Timestamps are `TIMESTAMP` columns (no time zone) filled by the database with `now()` or `clock_timestamp()` (ADR 005). Those functions return an absolute time, and storing it in a column without a zone converts it to the session's local time. The JDBC driver sets the session zone to the JVM's zone when it connects, which on the development machine is Australia/Sydney. When daylight saving time ends the clock goes back an hour, so a message written after the change can get an earlier `created_at` than one written before it, and chat history and agent memory (both ordered by `created_at`) come out in the wrong order for that hour. A review of the merged code showed this with a read-only query.

## Options considered

- **Change the columns to `TIMESTAMPTZ`** and the Java types to `OffsetDateTime`: the cleanest model, but it changes the schema (which only runs on a new database volume), every entity and response type, and the generated frontend types.
- **Add a sequence column as a tie-breaker:** fixes ordering only, and the times shown would still jump.
- **Run every database session in UTC:** the columns then hold UTC wall-clock time, which never jumps.
- **Accept it:** one hour a year on a local application.

## Decision

The third option. Each pooled connection runs `SET TIME ZONE 'UTC'` when it is created, and the SQL tool, which opens its own connections, sets the same inside its read-only transaction. A time zone in the JDBC URL (`options=-c TimeZone=UTC`) was tried and does not work: the driver sends the JVM zone at connect, which overrides it. Timestamps in API responses are therefore UTC, written without an offset, and the frontend reads them as UTC. Backend tests now always run with the JVM in Australia/Sydney (CI machines use UTC, which would hide the problem), and they check the session zone of the pool and of the SQL tool, and that a stored timestamp is UTC.

## Trade-offs

- The values carry no zone in the database or the API; "these are UTC" is a rule in the specs and this record, not something a type enforces. `TIMESTAMPTZ` remains the better model if the schema is ever migrated.
- Rows written before this change hold Sydney time, about ten hours ahead of new rows; on a development database they can be shifted once or the volume recreated.
- A session that changes its own zone would break the rule; no application code does, and the SQL tool's change is limited to its own transaction.
