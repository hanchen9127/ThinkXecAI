# ADR 001: CI with GitHub Actions against a real database

- **Status:** Accepted, 2026-10-02

## Context

Backend and frontend are developed together, and the backend's tests need PostgreSQL with the pgvector extension. Without automation, a broken test or a mismatch between the two sides only shows up when someone runs everything by hand.

## Options considered

- **No CI**, run checks locally before each merge.
- **CI with an in-memory database** (H2): fast, but it is not PostgreSQL and has no pgvector.
- **CI with GitHub's built-in service container**: real PostgreSQL, but it starts before the code is checked out, so it cannot run the project's schema scripts.
- **CI that starts the project's own `docker compose` setup**: the same database, schema and seed data as local development.

## Decision

Three GitHub Actions workflows run on every pull request: backend tests on the compose database, frontend lint and format checks, and a build of both. A database health check waits until the schema scripts have finished. CI uses placeholder secrets and never calls paid AI services; tests that need a real model are tagged and excluded.

## Trade-offs

- Each run takes about a minute because it starts a real database. Accepted: the tests then exercise the real database.
- The repository is private on a free plan, so GitHub cannot yet *block* a merge on a red check; checks are currently enforced by habit. Revisit when the repository goes public.
- Placeholder secrets mean CI proves the app starts and the logic works, not that real API keys work.
