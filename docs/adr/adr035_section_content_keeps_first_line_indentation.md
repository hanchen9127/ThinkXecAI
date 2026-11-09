# ADR 035: Section content keeps its first line's indentation

- **Status:** Accepted, 2026-10-17 (amends ADR 023)

## Context

ADR 023 made each Markdown section's content the original source between two headings, so code, tables, and lists reach the agent as written. The same rule also said the content is "trimmed", and the code removed all surrounding whitespace. In Markdown, four spaces of indentation make a line a code block, so a section that opens with an indented code block lost the indentation of its first line, and that line turned into a normal paragraph. A review found the two parts of the rule contradict each other.

## Options considered

- **Keep the full trim** and record the edge case as a known limit.
- **Remove only what carries no meaning:** blank lines before the content and whitespace after it.

## Decision

The second option. Leading lines that are empty or hold only spaces and tabs are removed, and trailing whitespace is removed; the first line keeps its indentation. Every other section is unchanged (headings are followed by a line break, so ordinary content never started with spaces that mattered). A test covers a section that opens with an indented code block after a blank line containing spaces.

## Trade-offs

- A first line indented by one to three spaces now keeps them; Markdown ignores such indentation, so the agent sees the same text.
- Documents uploaded before this change keep their stored content until they are uploaded again.
