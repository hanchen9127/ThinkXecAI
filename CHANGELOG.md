# Changelog

## 1.0.0 — 2026-11-09

The first release: every phase of the plan is done, and the whole system was verified end to end
against a real model (DeepSeek) with local `bge-m3` embeddings. The decisions behind it are in
[docs/adr/](docs/adr/README.md) (ADR 001–067).

### Agents and models

- Agents with a name, description, system prompt, chat model, allowed tools and knowledge bases, and
  chat options (temperature, top-p, memory window; temperature and top-p are stored but not yet used
  at run time).
- Three chat models through Spring AI: DeepSeek (`deepseek-chat`), Qwen (`qwen3.8-flash`), and an
  optional Gemini (`gemini-3.8-flash`). Each call has timeouts and at most three attempts.
- A Think-Execute loop of up to 20 steps that runs tools itself, stores every assistant and tool
  message, and ends on an answer, `terminate` with text, the step cap, or a stop. Messages to one
  chat run one after another; a run that cannot finish leaves a notice saying why.
- Stopping: `POST /api/chat-sessions/{id}/stop` ends the running run and those queued behind it,
  between steps, each with a "Stopped." notice.

### Tools and knowledge

- Fixed tools (knowledge search, terminate, city, date, sample weather) and optional ones: a
  read-only PostgreSQL query (one `SELECT` in a read-only transaction on its own connection, 5-second
  limit, 200 rows) and email through the Gmail API to an allowlist of recipients.
- Every tool result records its outcome: success, `UNKNOWN_TOOL`, `INVALID_ARGUMENTS`, `REFUSED`,
  or `TOOL_ERROR`, so failures can be shown and counted.
- Markdown upload into knowledge bases: all-or-nothing, split by heading, embedded with a local
  Ollama `bge-m3`, searched with pgvector within the agent's own knowledge bases.

### Live chat and frontend

- One Server-Sent Events stream per chat pushes each stored message as it is made; a second tab
  takes the stream over, and a deleted chat closes it.
- A React frontend for agents, knowledge bases, and chats. Each run is shown as a trace: steps on a
  rail, tool results summarised and opened in place, failed and refused calls marked, a timer while
  the agent works, Stop beside Send, and Try again on a run that did not answer. Deleting a chat
  or a document can be undone for 5 seconds; deleting an agent or a knowledge base asks first.
  Usable from the keyboard, with a skip link and focus kept in place.
- Errors say what failed and what to do next.

### Running it

- `docker compose up -d` starts PostgreSQL with pgvector; `docker compose --profile app up -d --build`
  also runs the backend and frontend in containers. Ports are published on `127.0.0.1` only, and the
  backend answers only requests addressed to `localhost` or `127.0.0.1`.

### Quality

- 609 backend tests against a real PostgreSQL and 366 frontend tests, run by CI on every pull
  request, with a check that the frontend's API types match the backend's OpenAPI description.
- From the tool system on (PR #13), every pull request was reviewed by two reviewers (correctness and
  security; specs, tests, and maintainability) before merging; the twelve earlier ones were reviewed
  the same way afterwards and their findings fixed together (PR #14). The UI went through five design
  reviews.

### Not included

Authentication and multiple users, WebSocket transport, token-by-token streaming, document formats
other than Markdown, and a layout designed for phones (narrow windows work, but are not refined).
