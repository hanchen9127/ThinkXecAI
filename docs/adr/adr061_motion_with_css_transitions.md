# ADR 061: Motion for feedback, 150 ms, with CSS transitions only

- **Status:** Accepted, 2026-11-04; amended 2026-11-06

## Context

Apart from Ant Design's own dialogs and menus and the pulsing working dots, nothing in the interface moved: results, steps, list deletes, and button states switched instantly, and the owner asked for smooth motion as feedback to what the user does. The owner chose each motion on interactive canvas boards (round 4).

## Options considered

- **Implementation:** CSS transitions with Tailwind classes; or an animation library (Motion, formerly Framer Motion), easier for enter, exit, and layout animation but another 30–60 kB dependency under ADR 051's pinning rule.
- **Timing:** crisp (150 ms, ease-out); soft (250 ms); a little spring (320 ms, overshoot).
- **Per motion,** three forms each for a new step, expand and collapse, the Send/Stop button, list deletes, and hover and press.

## Decision

CSS transitions only, no new dependency. Every transition runs 150 ms with `cubic-bezier(0.2, 0, 0, 1)`, declared once as a theme value, and Ant Design's motion tokens are set to match. A new step: the rail segment above it grows from the top, then its dot and content fade in 80 ms later. Expand and collapse (a tool result, Show steps, the fold when a run ends): `grid-template-rows` from `0fr` to `1fr` with the content sliding down 12 px, no fade. Send → Stop → Stopping…: the icon's shape changes from triangle to square, the labels cross-fade in place, Stopping… dims the button. A deleted list row folds away and the Undo bar rises 8 px; Undo reverses both. Hover and press change the background; pressing scales rows to 0.98 and buttons to 0.97. With `prefers-reduced-motion: reduce` every transition is off and states switch instantly. Motion only answers the user or a new message; nothing animates on page load.

**Amended 2026-11-06 (owner, M7, on the canvas).** Moving to another page was the one action with no feedback. The content now fades in while rising 8 px, for a section in the rail and an item in a list; the list panel does not move. A fade alone, and fading the list as well, were not chosen. Still CSS only: the content is mounted anew for each page, so `@starting-style` gives its first frame. There is no exit motion, because the old page is gone by then. The first page does not move, and a new chat becoming its session is not a page change.

**Amended 2026-11-09.** Send no longer turns into Stop (ADR 060 amended): Stop is its own button with a square, and only its label cross-fades from Stop to Stopping…, which dims it.

## Trade-offs

- An element that leaves (a deleted row) has to stay mounted until its transition ends, which takes a little code a library would hide.
- 150 ms is short enough that a slow device may skip frames unnoticed; it never delays the user.
- Tests check states and classes, not the motion itself.
