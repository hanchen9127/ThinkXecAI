# ADR 053: Fonts are bundled with the app

- **Status:** Accepted, 2026-11-02

## Context

The interface chosen for Phase 13 uses Instrument Sans for text and JetBrains Mono for code, tool arguments, and query results. Neither is installed on most systems, so the browser has to get them from somewhere. The app runs only on the user's own machine and its API is reachable only from it (ADR 033).

## Options considered

- **Google Fonts in `index.html`:** no dependency, but every page load asks Google for the fonts, sending the user's address and visit times to a third party; offline the page falls back to system fonts; the served version cannot be pinned.
- **System fonts only:** nothing to load, but the chosen typeface is lost (Segoe UI on Windows, San Francisco on macOS), so the app looks different on each system.
- **Fontsource packages from npm:** the font files are bundled by Vite and served by the app itself.

## Decision

Bundle the fonts with `@fontsource/instrument-sans` and `@fontsource/jetbrains-mono` (SIL Open Font License), pinned under ADR 051's 14-day rule, importing only the weights in use (Instrument Sans 400–700, JetBrains Mono 400 and 500). The app makes no request to a font service and looks the same offline.

## Trade-offs

- Two more dependencies to keep up to date.
- The build holds every character subset of each weight (about 0.5 MB in all). Each subset is declared with its `unicode-range`, so a browser downloads only the ones a page uses (Latin for this English interface).
