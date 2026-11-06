# ADR 051: Frontend versions raised, pinned exactly, and only after 14 days

- **Status:** Accepted, 2026-11-01

## Context

The frontend was set up in Phase 4 with the starting design's versions, and the hardening review left them unchanged until the pages were built. Before Phase 13, `npm audit` and the npm registry showed: `react-router-dom` 7.9.6 has a high-severity open redirect that can lead to XSS; `rolldown-vite` 7.2.5 is deprecated in favour of Vite 8; the whole ESLint 9 line is marked unsupported; and `@ant-design/x-markdown` 2.0.0, which will render the agent's answers, pulled a `dompurify` with a critical XSS advisory. Two listed dependencies, `react-contexify` and `emoji-picker-element`, were never imported.

## Options considered

- **Router:** patch to `react-router-dom` 7.18 (same API, fixes the advisory, a major upgrade still ahead) or move to `react-router` 8 (one package, needs React ≥ 19.2.7 and Node ≥ 22.22).
- **Build:** stay on `rolldown-vite` 7.3 (still deprecated) or move to Vite 8, which is built on Rolldown itself.
- **Lint:** latest ESLint 9 patch (no longer supported) or ESLint 10 (the config was already in the flat format).
- **Markdown renderer:** keep 2.0 and refresh the lockfile (fixes `dompurify` only) or move Ant Design X to the current 2.x line.
- **Which release to pin:** always the newest, or only releases that have been public for some time.

## Decision

Move to `react-router` 8, React 19.2.9 (same minor line, meets the router's floor), Vite 8 with the matching React and Tailwind plugins, ESLint 10 with current plugins, Ant Design 6.6 and Ant Design X / X Markdown 2.9; remove the two unused packages. The ESLint config uses ESLint's own `defineConfig`, as `typescript-eslint`'s `config()` helper is deprecated.

Every direct dependency is pinned to an exact version, and a version is pinned only after it has been published for at least 14 days. A compromised npm release is usually found and pulled within days, and first releases often get a quick patch; waiting two weeks stays out of both. In this change that held back `react-router` (8.4.0, not 8.5.1), Ant Design X (2.9.0, not 2.10.0), ESLint (10.13.0, not 10.14.0), and a few patch releases. The lockfile's `dompurify` and `zod` were moved to their fixed releases (3.4.17 and 4.6.7, both older than 14 days) within the ranges their parents allow.

Two advisories remain in runtime packages and are accepted: `katex` (prototype pollution) and `mermaid`, which depends on it. Ant Design X's ranges do not allow the fixed `katex`, and forcing it would run a combination its authors never tested. Neither is in the built app: `katex` is used only by X Markdown's opt-in LaTeX plugin and `mermaid` only by X's `Mermaid` component, and a test build with the chat components contained neither. An ESLint rule (`no-restricted-imports`) refuses those imports, so CI fails if one is added. The specs ask for neither math nor diagrams. Amended 2026-11-03: `@ant-design/x` was removed, since the chat ended up with its own step rail and composer and imported nothing from it; that also removed `mermaid` from the dependencies, so only the `katex` advisory remains (reported twice by `npm audit`: `katex` and X Markdown, which depends on it). The lint rule still refuses `katex`, `mermaid`, and X Markdown's plugins.

Fifteen moderate advisories remain in development tools only (a `minimatch` denial-of-service reached through ESLint, `typescript-eslint`, and `openapi-typescript`). They process only the project's own glob patterns and files, never reach the browser, and have no fixed release yet; they are accepted.

## Trade-offs

- `npm audit` is not clean: two low-severity runtime entries and fifteen moderate development entries stay until upstream releases fixes. Revisit at each upgrade.
- The 14-day wait delays security patches by up to two weeks. An advisory that affects code the app actually runs justifies an exception, recorded in a new ADR.
- Rendering math or diagrams later needs this decision revisited first.
- `react-router` 8 raises the Node requirement to 22.22; CI already uses the latest Node 22.
