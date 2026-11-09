# ADR 006: Native UUID mapping in the data layer

- **Status:** Accepted, 2026-10-03

## Context

All primary and foreign keys are PostgreSQL `uuid` values, and the Java model uses `java.util.UUID`. The data-access library, MyBatis 3.5, has no built-in support for UUIDs. A test against the real database showed both failure modes: writing a UUID field fails when the application starts, and reading silently skips UUID columns, so a newly created row came back with its id set to `null` and no error.

## Options considered

- **Use `String` ids everywhere** and cast in every SQL statement (`CAST(#{id} AS uuid)`), as the starting design did.
- **Change a database driver setting** so all text parameters are passed untyped, which affects every parameter, not just ids.
- **Add a small type handler** that passes `UUID` values to and from the PostgreSQL driver, which supports them natively.

## Decision

Add the type handler (about 40 lines plus unit tests). The Java model keeps real `UUID` types, the compiler prevents mixing ids with ordinary text, and SQL files need no casts. With the handler, the same database test passes for generated ids, UUID parameters, foreign keys and lists of ids.

## Trade-offs

- One more class to maintain, and it relies on the PostgreSQL driver's UUID support. Acceptable because PostgreSQL is a fixed part of this project.
- The failure without it is silent on reads, so every data-access test checks that ids come back non-null.
