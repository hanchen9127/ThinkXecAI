# ADR 023: Markdown sections keep their original source

- **Status:** Accepted, 2026-10-11

## Context

Uploaded Markdown is split at its headings. Each heading's text is embedded for search, and the section's content is what the agent reads when the section is retrieved. The starting design turned content into plain text and kept the original text only for tables, but only when the Markdown parser reports a table. The parser runs without extensions, and without the tables extension it never reports one. Tested on a sample, every table came out with all its rows on one line, link URLs and inline code markers were lost, and list items lost their markers.

## Options considered

- **Keep the specified rules**: known loss of tables, links, and code formatting.
- **Enable the parser's tables extension and keep plain text for everything else**: tables survive; links, inline code, and lists are still flattened.
- **Cut each section's content from the original text** between its heading and the next heading.

## Decision

Section content is the original Markdown between the end of its heading and the start of the next one, trimmed. The agent's models read Markdown well, so nothing needs converting, and tables, code blocks, lists, and links arrive exactly as written. The parser is still used, without extensions, to find the headings reliably: a `#` inside a code block or quote is not mistaken for one. Titles stay plain text with formatting removed, because they are what gets embedded. Line endings are normalized and a leading byte order mark is removed first; otherwise a file saved by some Windows editors would lose its first heading.

## Trade-offs

- Content can contain Markdown syntax noise (table pipes, HTML comments). It is not embedded, so search quality is unaffected.
- Text before the first heading is still not stored, as in the starting design. If documents with long introductions become common, it could become its own section.
- Very long sections are kept whole. Splitting them further is a separate decision if retrieved content becomes too large for the prompt.
