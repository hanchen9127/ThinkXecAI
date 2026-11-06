# ADR 041: The model gets one system message with the runtime instructions and the knowledge bases

- **Status:** Accepted, 2026-10-19

## Context

`KnowledgeTool` needs a knowledge-base id from the model, but nothing in the spec told the model which knowledge bases its agent may search. The starting design added a "decision module" prompt on every model call — complete the whole request, answer only when it is complete, use tools before asking the user, search the knowledge bases when context is missing — followed by its knowledge-base objects printed with `toString`. It sent this with `ChatClient.system(...)` while the agent's own system prompt was already in memory. Spring AI 1.1.8's Gemini model asserts "Only one system message is allowed in the prompt", so that pattern fails on Gemini.

A related gap: when the model calls a tool name the agent does not have (made up, or an optional tool not selected), Spring AI's default `ToolCallingManager` looks the name up in the application context and throws when it finds nothing, which ends the run.

## Options considered

- **Only a knowledge-base list** after the agent's prompt.
- **The starting design's guidance plus the list**, in English.
- **Nothing**: the first search would almost always fail; `KnowledgeTool`'s error text lists the allowed ids.

## Decision

The second option (chosen by the owner). The runtime builds **one** `SystemMessage` per run and puts it first in the in-run memory: the agent's `systemPrompt` (when set), a blank line, the guidance (principle 4, on answering in text before `terminate`, added by ADR 045), and either "You have no knowledge bases." or "Knowledge bases available to you (search them with KnowledgeTool, passing the id):" with one line `- <id>: <name> — <description>` per existing allowed knowledge base (whitespace in names and descriptions collapsed so an entry stays on one line) and "When context is missing, search these knowledge bases first." It reaches every model call and is never stored.

Tool names: the runtime builds its own `ToolCallingManager` whose resolver answers any name that is not among the run's callbacks with a tool returning "Unknown tool: <name>. Use only the tools you were given." The model sees the error and can correct itself; nothing outside the agent's tools can be resolved. Any other tool failure except an `Error` (rethrown: it ends the run without a notice and is logged by the scheduler, ADR 042), including arguments that are not JSON (Spring AI's default processor rethrows those, ending the run), is answered with "Tool <name> failed: <reason>." through the manager's own exception processor. Every tool result reaches the model and the database as JSON (`ToolJson`: a plain text becomes a JSON string), and memory rebuilt from stored rows normalizes results and call arguments the same way: Spring AI's Gemini model parses both as JSON and fails the whole request on anything else (PR #16 review, 2026-10-20).

Tests (scripted model, real database): exactly one system message, first, containing the agent's prompt, the guidance, and the knowledge-base line; no system row stored; a call to an optional tool the agent lacks is answered with the error text and the run continues. Leaving out the prompt and knowledge bases, or using Spring AI's default resolver, fails a test.

## Trade-offs

- The guidance overlaps with what a user may write in the agent's own prompt; both are sent.
- Knowledge-base names and descriptions are user text placed in the system message; only the local user can write them (ADR 033).
- A wrong tool name costs a model round trip instead of ending the run.
