# ADR 002: API responses return the resource directly

- **Status:** Accepted, 2026-10-02

## Context

Every endpoint wraps its result in `{ code, message, data }`. The starting design also wrapped `data` in a second, per-endpoint object (for example `{ agents: [...] }`), while the frontend expected the list itself. Both sides compiled, so the mismatch would only have surfaced as a runtime error once the UI was connected.

## Options considered

- **Keep per-endpoint wrapper objects** (`{ agents: [...] }`, `{ agent: {...} }`) and change the frontend to match.
- **Make `data` the resource itself**: a list, a single object, or `null` for deletes.

## Decision

`data` is the resource itself. The single `{ code, message, data }` envelope is enough; a second wrapper per endpoint added about 20 classes whose only job was to hold one field. The frontend already expected this shape, so it needed no change. Create and update endpoints return the full object, so the UI does not need a second request.

## Trade-offs

- If a list endpoint ever needs paging data (for example a total count), that one endpoint changes shape. Paging is out of scope; if it comes, a small `Page<T>` type would be added for that endpoint only.
- Diverges from the starting design's API, which is acceptable because the API contract is now written down in the specs.
- A planned CI step will generate the frontend types from the backend's API description, so the two sides cannot drift apart again.
