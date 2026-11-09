# ADR 067: Phone screens are out of scope

- **Status:** Accepted, 2026-11-09

## Context

ThinkXecAI runs on the user's own machine (the ports listen on the loopback address only, ADR 033), so it is used on a desktop browser. Each design critique still found work at phone width: the agent form at 390 px (2026-11-08 and 2026-11-09), a soft keyboard that cannot type a new line, touch targets under 44 px, labels on the narrow top bar. Each finding pulled time away from the desktop experience.

## Options considered

- Keep adapting every screen to phone width, as ADR 063 began.
- Declare phone screens out of scope and keep what ADR 063 already built.
- Remove the narrow layout as well.

## Decision

The owner declared phone screens out of scope. No new work is done for phone width, and design findings that apply only below the narrow breakpoint are not fixed; future critiques skip them (`.impeccable/critique/ignore.md`).

What ADR 063 built stays as it is (the fixed shell, the list or the item at narrow width, `/chat/new`), because the fixed shell is what makes the desktop layout work and removing the narrow screens would be work for nothing. It is kept working where a desktop change would break it, not improved.

## Trade-offs

- A phone or a very narrow window gets a usable but unpolished layout: cramped dialogs, small touch targets, Enter that always sends.
- Revisit if the app is ever served beyond the loopback address, where phones become a real audience.
