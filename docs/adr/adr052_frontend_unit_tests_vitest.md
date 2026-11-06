# ADR 052: Frontend unit tests run on Vitest with jsdom

- **Status:** Accepted, 2026-11-01

## Context

Until Phase 13 the frontend was only a typed API client, checked by the compiler, ESLint, and the API contract check. Phase 13 adds the pages, and with them logic a compiler cannot check: which messages a chat shows after a reconnect, when the "working" indicator turns off, what the client does with a reply that is not the backend's JSON envelope. The client's error handling had only been verified by a one-off script during the hardening review (PR #14). The test runner was deferred until the pages were built.

## Options considered

- **Vitest:** runs on the same Vite configuration and transform as the app, Jest-compatible API.
- **Jest:** the most widely used, but needs its own TypeScript and ES module transform next to Vite's, so two build setups to keep in step.
- **Node's built-in test runner:** no extra dependency, but no DOM and no JSX, so only pure functions could be tested.

## Decision

Vitest 5 with jsdom as the DOM, and React Testing Library (with `user-event` and the `jest-dom` matchers) for hooks and components; versions follow the 14-day rule of ADR 051. The test settings live in `vite.config.ts`, so tests compile exactly like the app. Tests sit next to the code as `*.test.ts(x)` and never call the backend: `fetch` and `EventSource` are replaced per test and restored afterwards. `npm test` runs in the `frontend-tests` CI workflow.

The first tests cover the HTTP client: path values are encoded and `.` / `..` refused; a success envelope returns its data; an error envelope throws its code and message; a reply that is not the JSON envelope (an HTML error page, an empty body, JSON `null`, or JSON without the JSON content type) keeps its HTTP status; an HTTP error status fails even when the body claims success. Each was checked by breaking the code on purpose and watching a test fail.

## Trade-offs

- jsdom is not a browser: layout, real `EventSource` reconnection, and rendering differences are not covered. Those stay with the manual checks before each pull request and Phase 14's end-to-end verification; a browser test runner can be added later if they keep breaking.
- Six more development dependencies to keep updated under ADR 051's rule.
