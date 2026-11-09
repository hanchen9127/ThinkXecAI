# ADR 021: CI fails when the backend API and the frontend types drift apart

- **Status:** Accepted, 2026-10-09

## Context

The frontend talks to the backend through typed functions. When those types were written by hand, a backend change such as a renamed field still compiled on both sides and only failed at runtime in the browser. This happened in the starting design: its hand-written frontend types disagreed with the backend in several places. ADR 020 made the backend describe its API exactly; this decision is about enforcing that description.

## Options considered

- **Keep hand-written frontend types and review carefully**: relies on people noticing every change.
- **Generate the types during the frontend build**: always fresh, but the build then needs the backend running, and API changes never show up in a code review.
- **Commit the generated types and let CI check them**: the generated file is part of the code under review, and CI fails if it is out of date or if the frontend code no longer fits it.

## Decision

The frontend's request and response types are generated from the backend's API description into a committed file, and the API functions derive their types from it by endpoint path. TypeScript's strict mode is on, so "may be null" is enforced. A CI job on every pull request starts the database, exports the description from the backend tests, regenerates the types, and fails if the result differs from the committed file or if the frontend no longer typechecks. Compile-time tests pin a few derived types so a broken type helper cannot hide drift. Both kinds of drift were reproduced on purpose and made the check fail.

## Trade-offs

- A backend API change needs two extra commands before committing (run the backend tests, then `npm run gen:api`); CI says so when they are missing.
- The check job needs a database and takes a few minutes, because the description is produced by starting the backend.
- Only what the description covers is checked: the SSE stream keeps hand-written types. (When this was decided the upload and tool endpoints were not described yet; they are now.)
