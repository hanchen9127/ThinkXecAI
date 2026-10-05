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
  always available; optional tools (read-only PostgreSQL query, QQ email sending) are
  selected per agent.
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
| Backend   | Java 21, Spring Boot 3.5.16, Spring AI 1.1.0, MyBatis, PostgreSQL JDBC      |
| AI models | Spring AI `ChatClient` over DeepSeek (`deepseek-chat`) and OpenAI (`gpt-5.4-mini`) |
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
- [ ] Phase 7 — Facade services and REST APIs
- [ ] Phase 8 — Markdown ingestion and RAG
- [ ] Phase 9 — Tool system
- [ ] Phase 10 — Chat model registry
- [ ] Phase 11 — Agent runtime (Think-Execute loop)
- [ ] Phase 12 — SSE
- [ ] Phase 13 — Frontend features (full UI)
- [ ] Phase 14 — End-to-end verification

The backend's data layer is in place, but there are no REST endpoints yet (Phase 7), so the
backend starts without serving any API and the frontend runs as an empty shell.

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

Required variables:

| Variable                      | Purpose                          |
|--------------------------------|-----------------------------------|
| `THINKXECAI_DB_PASSWORD`       | PostgreSQL password               |
| `THINKXECAI_MAIL_USERNAME`     | QQ mail account for the email tool|
| `THINKXECAI_MAIL_PASSWORD`     | QQ mail authorization code        |
| `THINKXECAI_OPENAI_API_KEY`    | OpenAI API key (`gpt-5.4-mini`)   |
| `THINKXECAI_DEEPSEEK_API_KEY`  | DeepSeek API key (`deepseek-chat`)|

The backend fails fast at startup if any of these are missing.

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts `pgvector/pgvector:pg18` on `localhost:5433`, database `thinkxecai`, and runs
the initialization scripts in `db/init/` (core schema, vector index, and sample
e-commerce data used by the database query tool).

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`, with all REST endpoints under `/api` and SSE
under `/sse`. Env vars in `.env` need to be loaded into the process environment (e.g. via
your shell, an IDE run configuration, or a tool like `direnv`).

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

Errors use `code: 500` (or a specific error code) with `data: null`.

| Resource        | Endpoints                                                                 |
|-----------------|---------------------------------------------------------------------------|
| Agents          | `GET/POST /agents`, `PATCH/DELETE /agents/{agentId}` |
| Chat sessions   | `GET/POST /chat-sessions`, `GET/PATCH/DELETE /chat-sessions/{chatSessionId}`, `GET /chat-sessions/agent/{agentId}` |
| Chat messages   | `GET /chat-messages/session/{sessionId}`, `POST /chat-messages`, `PATCH/DELETE /chat-messages/{chatMessageId}` |
| Knowledge bases | `GET/POST /knowledge-bases`, `PATCH/DELETE /knowledge-bases/{knowledgeBaseId}` |
| Documents       | `GET/POST /documents`, `GET /documents/kb/{kbId}`, `POST /documents/upload`, `PATCH/DELETE /documents/{documentId}` |
| Tools           | `GET /tools` (optional tools only) |

Live agent output streams from `GET /sse/connect/{chatSessionId}` (`text/event-stream`):
an `init` event on connect, then `message` events carrying JSON of the form
`{ "type": ..., "payload": { "message", "statusText", "done" }, "metadata": { "chatMessageId" } }`.

## License

[MIT](LICENSE)
