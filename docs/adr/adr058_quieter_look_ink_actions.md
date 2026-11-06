# ADR 058: A quieter look — stone ground, ink actions, colour only for Think and Execute

- **Status:** Accepted, 2026-11-04 (replaces the Phase 13 V1 palette in tech-stack → Frontend → Look); amended 2026-11-06

## Context

A design review of the finished frontend (Impeccable critique, 26/40) and the owner both found the interface generic, "AI-flavoured": a cool slate ground with an indigo primary is the most common default pairing of generated interfaces, indigo also coloured every primary button (so the colour meant to mark a Think step meant nothing), tools and models sat in tinted pill tags, and the rail used a robot icon. The owner chose a replacement element by element on the design canvas (round 1).

## Options considered

- **Ground:** stone, light (neutral warm grey); graphite, dark (an IDE-like workbench); sage, light (green-grey paper). A cream ground with a clay accent was not offered: it is itself a common default of generated pages.
- **Primary action:** ink solid; outlined everywhere with only Send filled; text actions with fill only to commit.
- **Monospace:** identifiers only; also numbers and instructions; also the whole step trace.
- **Tags:** outlined; text after a small mark; a plain comma list.
- **Rail:** stock icons without the robot; own glyphs with a two-colour monogram; labelled icons.

## Decision

Stone ground `#F3F3F0` with white surfaces, ink `#1F2226`, muted `#5C6066`, line `#DEDED9`, rail `#2A2D31`. Primary buttons are ink with white text; secondary buttons are outlined; Delete moves into a ⋯ menu. Think indigo `#4453C4` and Execute orange `#B4560A` are kept and used only for those two step kinds. JetBrains Mono is used for model ids and tool names only. Tags become text: a tool after a small Execute-orange square, a knowledge base after a document glyph, a model id as muted mono text. The rail keeps Ant Design icons without `RobotOutlined`, each with a text label, and a white focus ring (the old indigo ring was 2.44:1 on the rail). The wordmark stays at the top of the list panel, smaller than a page heading. The list panel's New button stays solid as well (owner's choice over an outlined one). Fonts, radii, and layout are unchanged; the palette is still declared once in `theme.ts` and once in `index.css`, kept equal by a test.

**Amended 2026-11-06 (owner, on the canvas, after using the redesign).** Layout: beside the list panel, left-aligned content read as pushed left with empty space beside it, so every page now sits in one column centred in the pane, 920 px wide at most. Left-aligned and wider, and tool pages filling the pane with only the chat centred, were not chosen. Header bands still span the pane and hold their text in the column. Selected option: Ant Design derived a grey from the ink primary; the row is now the stone ground with semibold text and a check mark, set by tokens, also while the row is active. An ink row with white text and a white row with only a check were not chosen; a 2 px ink bar was dropped because it needs an override of Ant Design's classes.

**Amended 2026-11-06 (owner, from a page walkthrough).** Placeholders use the muted colour (6.3:1 on white; Ant Design's default is 1.8:1). Links and plain buttons share a 2 px ink focus ring. The upload drop zone is a white surface with a visible dashed border, as in the mockup. The agent list puts the model on its own line, with tool and knowledge counts as 24 px targets below it. The delete dialog's underlined field stays.

## Trade-offs

- It is a restyle of the existing Ant Design and Tailwind setup, not a new design system (a mission non-goal).
- A screen can show two solid buttons (panel and content); the owner accepted that.
- A very wide window leaves equal space on both sides of the column.
- Think and Execute differ in hue more than lightness; their labels carry the difference for colour-blind users, as before.
