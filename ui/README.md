# ThinkXecAI frontend

React 19 + TypeScript + Vite (`rolldown-vite`) + Ant Design. The pages arrive in Phase 13; for now
this package holds the typed API client the pages will use. See the repository README for running
the whole system.

## Scripts

| Script                            | What it does                                                                       |
| --------------------------------- | ---------------------------------------------------------------------------------- |
| `npm run dev`                     | Vite dev server; the backend must run on `http://localhost:8080`                   |
| `npm run build`                   | Type-check and build into `dist/`                                                  |
| `npm run typecheck`               | `tsc -b`, including the compile-time API type checks in `src/api/api.type-test.ts` |
| `npm run lint`                    | ESLint; any warning fails                                                          |
| `npm run format` / `format:check` | Prettier                                                                           |
| `npm run gen:api`                 | Regenerate `src/api/schema.d.ts` from `../backend/target/openapi.json`             |

## API client

- `src/api/http.ts`: `fetch` wrapper that unwraps the backend's `{ code, message, data }` envelope and
  throws `ApiError` otherwise (also for a non-JSON reply, with its HTTP status). Path ids go through
  the `path` tag, which encodes them.
- `src/api/schema.d.ts`: generated from the backend's OpenAPI description; never edit it by hand. CI
  regenerates it and fails when the committed copy differs.
- `src/api/api.ts`: one typed function per endpoint, with request and response types derived from the
  generated schema by endpoint path.

After changing a backend request or response, run the backend tests (they export
`backend/target/openapi.json`), then `npm run gen:api`, and commit the regenerated file.
