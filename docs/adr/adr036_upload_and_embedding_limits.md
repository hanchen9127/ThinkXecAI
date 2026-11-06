# ADR 036: Uploads and embedding calls have limits

- **Status:** Accepted, 2026-10-17

## Context

A review of the merged upload path measured several ways one file could tie up the server: a 1 MB file of one-word headings becomes 262,144 sections, all sent to Ollama in a single request and written as rows in one transaction; about 10,000 nested list markers (20 KB) overflow the Markdown parser's stack and returned a generic server error (500); a NUL character is valid UTF-8 but cannot be stored by PostgreSQL, so the upload failed only after the slow embedding call; and Ollama calls had no read timeout, so a hanging Ollama held an upload, and since the knowledge tool searches through it, an agent's tool call, forever. The same NUL problem made any JSON text field return 500, and a cut-off multipart body did too. The parser can also take minutes of CPU on some crafted files under 1 MB.

## Options considered

- **Only the Ollama timeout:** fixes the hang that blocks agents, leaves the rest.
- **Common limits, and accept the parser's worst case:** cap sections, refuse NUL and too-deep nesting as client errors, time out Ollama.
- **Also run the parser under a time limit:** the parser cannot be interrupted, so a timeout would answer the request while the CPU stays busy; little gain for the code it needs.

## Decision

The second option. An upload may have at most 500 sections; text with NUL characters and documents nested too deeply to parse are refused with 400 before the embedding call. The application builds Ollama's client itself so it has a 5-second connect timeout and a configurable read timeout (`thinkxecai.ollama.read-timeout`, 60 seconds); the chat models keep their own clients. A NUL character in any JSON text of a request body, or in an uploaded file's name, is refused with 400 (naming the field where there is one), and a malformed multipart body with 400. The check sits on Spring MVC's own JSON converter, not on the shared ObjectMapper, so replies from the chat models are not affected. The parser's worst-case CPU time is accepted: since ADR 033 only the local user can upload. Tests cover 500 and 501 sections, NUL in a file and in JSON, a 20,000-level nested list, a cut-off multipart body against a real server, and a fake Ollama that answers too late.

## Trade-offs

- 500 sections is generous for a document but not for a whole manual in one file; it can be split.
- A slow but working Ollama (a cold model load on a weak machine) can exceed 60 seconds; the setting can be raised.
- Catching a stack overflow is unusual in Java; it is safe here because the parser holds no locks or shared state, and the error is turned into a normal 400.
- Found while testing: rows rolled back by tests stay in the vector index until the table is vacuumed, so a test that stores many identical vectors can disturb another test's index search. Test data now uses distinct vectors.
