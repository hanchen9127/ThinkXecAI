# ThinkXecAI

[![backend-tests](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/backend-tests.yml/badge.svg)](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/backend-tests.yml)
[![frontend-tests](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/frontend-tests.yml/badge.svg)](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/frontend-tests.yml)
[![build](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/build.yml/badge.svg)](https://github.com/hanchen9127/ThinkXecAI/actions/workflows/build.yml)

A Java AI agent platform for reasoning, tool execution, RAG, and real-time SSE feedback.

ThinkXecAI is a configurable AI agent assistant: create agents backed by different chat
models, give them tools and knowledge bases, chat with them, and watch generated messages
stream in live. It's built with Spring Boot + Spring AI on the backend, React + Ant Design
on the frontend, and PostgreSQL + pgvector for storage and retrieval.

## Features

- **Configurable agents** — name, description, system prompt, chat model, allowed tools,
  allowed knowledge bases, and chat options (temperature, top-p, memory window).
- **Think-Execute agent loop** — restores recent chat memory, calls the selected model,
  detects and manually executes tool calls, persists generated messages, and stops on
  completion, `terminate`, or a 20-step cap.
- **Tool system** — fixed tools (knowledge retrieval, terminate, city, date, weather) are
  always available; optional tools (read-only PostgreSQL query, email through Gmail) are
  selected per agent. Knowledge retrieval only searches the agent's own knowledge bases; SQL
  runs in a read-only transaction with a 5-second limit and a 200-row cap; email goes only to an
  allowlist of recipients.
- **Knowledge bases & RAG** — upload Markdown documents, parse them into sections, embed
  section titles with a local Ollama `bge-m3` model, and retrieve the top matching chunks
  via pgvector similarity search.
- **Chat history** — every user, assistant, and tool message is persisted in PostgreSQL.
- **Live updates over SSE** — one Server-Sent Events connection per chat session streams
  generated assistant and tool messages to the frontend as they're produced.
- **React frontend** — sidebar layout with tabs for agents, chats, and knowledge bases;
  modals for creating agents/knowledge bases; a chat view with live streaming updates.

Out of scope by design: authentication/authorization, multi-tenancy, WebSocket transport,
token-by-token streaming, and document formats other than Markdown.

## Tech Stack

| Layer     | Technology                                                                 |
|-----------|-----------------------------------------------------------------------------|
| Backend   | Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, MyBatis, PostgreSQL JDBC      |
| AI models | Spring AI `ChatClient` over DeepSeek (`deepseek-chat`), Qwen (`qwen3.8-flash`, through its OpenAI-compatible API), and Google Gemini (`gemini-3.8-flash`) |
| Database  | PostgreSQL (`pgvector/pgvector:pg18`) with the `vector` extension           |
| Embeddings| Local Ollama, model `bge-m3`, 1024-dim vectors                              |
| Frontend  | React 19, TypeScript, Vite (`rolldown-vite`), Tailwind CSS, Ant Design + `@ant-design/x` |
| Realtime  | Server-Sent Events (SSE), one connection per chat session                   |
## Project Status

This project is being built phase by phase. Current status:

- [x] Phase 1 — Repository baseline (`docker-compose.yml`, `backend/`, `ui/`)
- [x] Phase 2 — Database schema (`db/init/*.sql`, core tables + sample e-commerce data)
- [x] Phase 3 — Backend project setup (Spring Boot app, config, secrets validation)
- [x] Phase 4 — Frontend setup (React/Vite app, typed API client)
- [x] Phase 5 — Common backend foundation (`ApiResponse`, DTOs, entities, converters)
- [x] Phase 6 — MyBatis persistence layer (six mappers, tested against PostgreSQL + pgvector)
- [x] Phase 7 — Facade services and REST APIs (CRUD for five resources, request validation, CI contract check)
- [x] Phase 8 — Markdown ingestion and RAG (all-or-nothing upload, heading-based sections, local `bge-m3` embeddings, filtered vector search)
- [x] Phase 9 — Tool system (fixed and optional tools, read-only SQL, Gmail with a recipient allowlist, `GET /tools`)
- [ ] Phase 10 — Chat model registry
- [ ] Phase 11 — Agent runtime (Think-Execute loop)
- [ ] Phase 12 — SSE
- [ ] Phase 13 — Frontend features (full UI)
- [ ] Phase 14 — End-to-end verification

The REST API is in place except SSE (Phase 12). Uploaded Markdown is split into sections,
embedded, and searchable per knowledge base, and the tools are ready, but no agent uses them until
the chat model registry and agent runtime arrive (Phases 10 and 11). Creating a user message stores
it and emits a chat event, but nothing runs the agent until Phase 11. The frontend has a typed API
client and still runs as an empty shell.

## Getting Started

### Prerequisites

- Java 21 (e.g. Eclipse Temurin) and Maven
- Node.js 20+ and npm
- Docker (for PostgreSQL)
- [Ollama](https://ollama.com/) running locally with the `bge-m3` model pulled:
  ```bash
  ollama pull bge-m3
  ```

### 1. Configure secrets

Copy the example env file and fill in real values (never commit `.env`):

```bash
cp .env.example .env
```

Required variables (the backend fails fast at startup if any is missing):

| Variable                      | Purpose                             |
|-------------------------------|-------------------------------------|
| `THINKXECAI_DB_PASSWORD`      | PostgreSQL password                 |
| `THINKXECAI_DEEPSEEK_API_KEY` | DeepSeek API key (`deepseek-chat`)  |
| `THINKXECAI_QWEN_API_KEY`     | Alibaba Cloud Model Studio API key (`qwen3.8-flash`), international site |

Optional: `THINKXECAI_GEMINI_API_KEY`, the Gemini API key (`gemini-3.8-flash`). Without it the backend
still starts, and an agent set to Gemini answers that its model is not available. Gemini is kept but no
longer tested against the real API: on the free tier it took 56–109 seconds per answer.

Messages, documents found by the knowledge tool, and tool results are sent to the chosen model's
provider. On Gemini's free tier Google may use submitted content to improve its products, so use a
paid key (or DeepSeek or Qwen) before working with private data.

The Qwen key must come from Model Studio's international site (Singapore): the backend calls
`https://dashscope-intl.aliyuncs.com/compatible-mode`, and such keys are refused by the mainland and
US endpoints.

Optional variables, only for the email tool. Startup never checks them; until all four Gmail
values are set, the tool answers that email is not configured and names what is missing.

| Variable                             | Purpose                                                   |
|--------------------------------------|-----------------------------------------------------------|
| `THINKXECAI_GMAIL_SENDER`            | The Gmail address that sends                              |
| `THINKXECAI_GMAIL_CLIENT_ID`         | OAuth client ID (see below)                               |
| `THINKXECAI_GMAIL_CLIENT_SECRET`     | OAuth client secret                                       |
| `THINKXECAI_GMAIL_REFRESH_TOKEN`     | Refresh token granted the `gmail.send` scope              |
| `THINKXECAI_MAIL_ALLOWED_RECIPIENTS` | Comma-separated addresses the tool may send to; empty means no email is sent |

#### Gmail setup (one time, only for the email tool)

The tool sends through the Gmail API with OAuth 2.0 and asks only for the send-only scope
`https://www.googleapis.com/auth/gmail.send`: it cannot read or delete mail, and no password or
App Password is used.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project and enable
   the **Gmail API**.
2. Configure the **OAuth consent screen** (user type *External*), add your Gmail address as a
   user, and add the scope `https://www.googleapis.com/auth/gmail.send`. Set the publishing
   status to **In production**: while it is *Testing*, Google expires refresh tokens after 7 days.
   An unverified app in production still works for its owner, after an "unverified app" warning
   during consent.
3. Create an **OAuth client ID** of type **Desktop app** and note its client ID and secret.
4. Get a refresh token once. Open this URL in a browser (fill in your client ID), sign in with
   the sending account, and allow access:

   ```
   https://accounts.google.com/o/oauth2/v2/auth?client_id=CLIENT_ID&redirect_uri=http://127.0.0.1:8765&response_type=code&scope=https://www.googleapis.com/auth/gmail.send&access_type=offline&prompt=consent
   ```

   The browser then fails to load `http://127.0.0.1:8765/?code=...` (nothing listens there). Copy
   the `code` value from the address bar and exchange it:

   ```bash
   curl -s https://oauth2.googleapis.com/token \
     -d client_id=CLIENT_ID -d client_secret=CLIENT_SECRET \
     --data-urlencode "code=CODE_FROM_THE_ADDRESS_BAR" \
     -d grant_type=authorization_code -d redirect_uri=http://127.0.0.1:8765
   ```

   The `refresh_token` in the answer is `THINKXECAI_GMAIL_REFRESH_TOKEN`.
5. Set the four Gmail variables and `THINKXECAI_MAIL_ALLOWED_RECIPIENTS` in `.env`. To check the
   setup, send one real email to the first allowed address:

   ```bash
   cd backend
   mvn test -Dtest=EmailToolExternalTest -DexcludedGroups= -Dgroups=external
   ```

The tool sends synchronously and tells the agent the real outcome (sent, refused, not configured,
rejected by Google, or timed out after 10 seconds). It sends to exactly one address per call, and
only when that address is on the allowlist.

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts `pgvector/pgvector:0.8.6-pg18` on `localhost:5433` (published on `127.0.0.1` only), database
`thinkxecai`, and runs the initialization scripts in `db/init/` (core schema, vector index, and sample
e-commerce data used by the database query tool).

The scripts run only when the data volume is empty, so a database created by an older version of
`db/init/` keeps its old schema. To start over (this deletes all data), and to check that the scripts
succeeded — if one fails, the container restarts and then runs without finishing them:

```bash
docker compose down -v
docker compose up -d --wait
docker compose logs postgres | grep -E "ERROR|init process complete"
```

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`, with all REST endpoints under `/api` and SSE
under `/sse`. It has no authentication, so it listens on `127.0.0.1` only and answers only requests
addressed to `localhost` or `127.0.0.1` (others get HTTP 403); open it as `http://localhost:8080`,
not by a LAN address. Env vars in `.env` need to be loaded into the process environment (e.g. via
your shell, an IDE run configuration, or a tool like `direnv`).
Uploaded files are stored under `data/documents/` relative to the working directory (gitignored).
The backend starts without Ollama; it is needed (and given 60 seconds per call) when a document is uploaded or a knowledge base is
searched.
Chat model calls get 120 seconds to answer (`thinkxecai.chat.read-timeout`), and a failing call is tried
at most 3 times, 2 and then 6 seconds apart (`spring.ai.retry.*`). DeepSeek and Qwen also get 10 seconds to connect
(`thinkxecai.chat.connect-timeout`) and the 120 seconds per attempt; for Gemini, whose client has no
separate connect timeout, the 120 seconds cover the whole call, and a call that hangs ends 8 seconds later
(the remaining waits between attempts).

### 4. Run the frontend

```bash
cd ui
npm install
npm run dev
```

The Vite dev server runs on its default port and talks to the backend at
`http://localhost:8080/api`.

## Project Structure

```
backend/      Spring Boot application (controllers, services, mappers, agent runtime)
ui/           React + TypeScript + Vite frontend
db/init/      PostgreSQL initialization SQL (schema, indexes, sample data)
docker-compose.yml   PostgreSQL with pgvector
```

## API

All JSON REST endpoints live under `/api` and return a consistent envelope:

```json
{ "code": 200, "message": "success", "data": { } }
```

The HTTP status is always 200; errors set `code` and `data: null`, with an English `message`:
`400` for invalid input (the message names the field), `404` for a missing resource, `409` for a
conflict (such as a user message to a session whose agent was deleted), and a generic `500` for
anything else. The one exception is a request whose `Host` is not `localhost` / `127.0.0.1`: it
never reaches the API and gets HTTP 403 (with the same envelope, code `403`).

Timestamps (`createdAt`, `updatedAt`) are UTC, written without an offset (`2026-10-17T03:15:00`).

| Resource        | Endpoints                                                                 |
|-----------------|---------------------------------------------------------------------------|
| Agents          | `GET/POST /agents`, `PATCH/DELETE /agents/{agentId}` |
| Chat sessions   | `GET/POST /chat-sessions`, `GET/PATCH/DELETE /chat-sessions/{chatSessionId}`, `GET /chat-sessions/agent/{agentId}` |
| Chat messages   | `GET /chat-messages/session/{sessionId}`, `POST /chat-messages` (user messages only), `DELETE /chat-messages/{chatMessageId}` |
| Knowledge bases | `GET/POST /knowledge-bases`, `PATCH/DELETE /knowledge-bases/{knowledgeBaseId}` |
| Documents       | `GET/POST /documents`, `GET /documents/kb/{kbId}`, `POST /documents/upload`, `PATCH/DELETE /documents/{documentId}` |
| Tools           | `GET /tools` (optional tools only) |

`POST /documents/upload` takes a `multipart/form-data` form with `kbId` and one `file`: a UTF-8
Markdown file (`.md` or `.markdown`, at most 1 MB, no NUL characters) with at least one and at most 500
headings with text. Each heading starts a section; the section titles are embedded and the original
Markdown below each heading is stored
as the chunk. The upload either fully succeeds or leaves no document, chunk, or file behind.
`GET /tools` returns the optional tools an agent can choose, as `{ name, description, type }`;
fixed tools are given to every agent and are not listed. An agent's `allowedTools` may only name
optional tools (`dataBaseTool`, `emailTool`); anything else is rejected with `400`. Outside `/api`,
`GET /health` and `GET /sse-test` return plain text `ok`.

Live agent output streams from `GET /sse/connect/{chatSessionId}` (`text/event-stream`):
an `init` event on connect, then `message` events carrying JSON of the form
`{ "type": ..., "payload": { "message", "statusText", "done" }, "metadata": { "chatMessageId" } }`.

### API contract

The backend describes its API with OpenAPI (`/v3/api-docs`), and the frontend's request and
response types are generated from it into `ui/src/api/schema.d.ts`. After changing the API, run
the backend tests (they export `backend/target/openapi.json`) and then `npm run gen:api` in `ui/`,
and commit the regenerated file. The `build / contract` CI job fails when the two sides differ.

## License

[MIT](LICENSE)
