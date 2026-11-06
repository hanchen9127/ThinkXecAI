# ADR 004: Typed metadata for chat messages

- **Status:** Accepted, 2026-10-02

## Context

When the agent calls a tool, the assistant message records which tools it called, and each tool message records the result. These details are stored as JSON next to the message. Later the agent rebuilds its conversation memory from those stored messages, so what it writes must be exactly what it can read back. The starting design stored this as a free-form map.

## Options considered

- **Free-form map** (`Map<String, Object>`): flexible, but nothing checks the shape.
- **The AI framework's own classes**: exact match today, but the stored JSON would change whenever the framework changes.
- **Small project-owned types**: `toolCalls` (id, name, arguments) for assistant messages, `toolResponse` (id, name, responseData) for tool messages.

## Decision

Use project-owned types, with matching types in the frontend. The stored JSON has a fixed, documented shape that is independent of the AI framework's version, and the compiler catches mistakes. Tests check that both message kinds survive a save-and-load round trip, that the stored JSON has exactly the documented fields, and that unknown fields are ignored.

## Trade-offs

- The agent runtime has to convert between these types and the framework's types; a few lines of mapping code.
- Adding a new kind of metadata requires a code change. That is intended: the shape is a contract.
