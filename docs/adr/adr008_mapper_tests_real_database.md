# ADR 008: Data-layer tests run on the real database and roll back

- **Status:** Accepted, 2026-10-04

## Context

The data layer is mostly SQL: vector search, PostgreSQL JSON columns, foreign-key cascades, generated ids and timestamps. These behaviors only exist in PostgreSQL, so tests that do not use PostgreSQL would not test them. Tests also must not leave data behind or depend on each other.

## Options considered

- **In-memory database (H2)**: fast, no setup, but different SQL behavior and no pgvector.
- **Testcontainers**: a fresh PostgreSQL container per test run; realistic, but adds a dependency and startup time on top of the database CI already starts.
- **The project's own PostgreSQL** (Docker Compose locally and in CI), with every test wrapped in a transaction that is rolled back afterwards.

## Decision

Use the project's own PostgreSQL with rollback. The tests run against exactly the schema the application uses. A shared base class makes each test transactional and creates any parent rows with plain SQL rather than other data-access classes, so each test fails only when the class it tests is wrong. The Spring context starts once for all of these tests. Checked that the rollback really happens: removing it makes the rollback test fail.

## Trade-offs

- Docker must be running to run the backend tests locally.
- Because every test rolls back, behavior that only happens on commit (for example "after commit" event listeners) is not covered here; those features get their own tests.
- Tests share one database, so they assert only on rows they created themselves.
