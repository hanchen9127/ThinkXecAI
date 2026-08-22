# ThinkXecAI Roadmap

## Rebuild Target

Build ThinkXecAI from an empty repository until it reaches the feature set defined by this constitution. All technologies, runtime behavior, API contracts, database structures, and frontend workflows must match the constitution. The frontend project is created early to validate contracts and support integration testing; this ordering does not change the approved tech stack.

## Phase 1: Repository Baseline

1. [x] Create the repository root.
2. [x] Add `specs/mission.md`, `specs/tech-stack.md`, and `specs/roadmap.md`.
3. [x] Add `docker-compose.yml` for PostgreSQL with `pgvector/pgvector:pg18`, host port `5433`, database `thinkxecai`, user `postgres`, and initialization scripts from `db/init`.
4. [x] Create backend folder `backend` and frontend folder `ui`.

Acceptance criteria:

- `docker compose up -d` starts PostgreSQL.
- The repository contains backend, frontend, database initialization, and specs directories.

## Phase 2: Database Schema

1. [x] Create `db/init/001_thinkxecai.sql`.
2. [x] Enable the `vector` extension.
3. [x] Create tables `agent`, `chat_session`, `chat_message`, `knowledge_base`, `document`, and `chunk_bge_m3`.
4. [x] Use UUID primary keys with `gen_random_uuid()`.
5. [x] Add all foreign keys, JSONB columns, timestamp columns, and `VECTOR(1024)` embedding column.
6. [x] Add the `ivfflat` index on `chunk_bge_m3.embedding`.
7. [x] Add a second initialization script for the simple sample e-commerce schema used by the database query tool.
8. [x] Add a third initialization script for sample e-commerce seed data.

Acceptance criteria:

- A fresh PostgreSQL container initializes without SQL errors.
- All tables and the vector index exist.
- The sample e-commerce tables and seed rows exist.

## Phase 3: Backend Project Setup

1. [x] Create a Java 17 Spring Boot Maven project.
2. [x] Add the exact backend dependencies from `tech-stack.md`.
3. [x] Configure application properties for datasource, mail, DeepSeek, OpenAI `gpt-5.4-mini`, MyBatis aliases, mapper XML locations, type handlers, and document storage.
4. [x] Create the Spring Boot main application class.
5. [x] Configure `application.yaml` so every secret is read from environment variables and no real secret is stored in the file.
6. [x] Add `.env` to `.gitignore`.
7. [x] Add `.env.example` with every required secret variable and placeholder values only.
8. [x] Add CORS configuration compatible with the frontend development server.
9. [x] Add async execution configuration.

Acceptance criteria:

- `mvn test` starts the Spring context when external services are available or mocked.
- The app can connect to PostgreSQL on `localhost:5433`.
- `application.yaml` contains no real API keys, passwords, tokens, or mailbox authorization codes.
- `.env` is ignored by git.
- `.env.example` is committed and contains all required secret variable names.

## Phase 4: Frontend Setup

1. [x] Create a React 19 and TypeScript Vite app.
2. [x] Add the exact frontend dependencies from `tech-stack.md`.
3. [x] Configure Tailwind CSS and ESLint.
4. [x] Implement the shared API wrapper with `BASE_URL = "http://localhost:8080/api"`.
5. [x] Implement typed API functions for all backend endpoints.

Acceptance criteria:

- `npm run build` succeeds.
- API functions match backend request and response shapes.
- The frontend app can run as an empty shell before all backend endpoints are implemented.

## Phase 5: Common Backend Foundation

1. [ ] Implement `ApiResponse<T>` with code, message, and data fields.
2. [ ] Implement `BizException`.
3. [ ] Implement a global REST exception handler.
4. [ ] Implement `AgentState` with values `IDLE`, `PLANNING`, `THINKING`, `EXECUTING`, `FINISHED`, and `ERROR`.
5. [ ] Implement entities, DTOs, request objects, response objects, and VOs for all six domain tables.
6. [ ] Implement converters between requests, DTOs, entities, and VOs.
7. [ ] Preserve JSONB metadata serialization behavior in converters.
8. [ ] Implement `PgVectorTypeHandler` for `float[]`.

Acceptance criteria:

- All domain objects compile.
- `ApiResponse.success()` returns code `200`, message `success`, and null data.
- `ApiResponse.error(...)` returns code `500` unless a specific error code is supplied.
- JSON metadata round-trips between DTOs and database entities.

## Phase 6: MyBatis Persistence

1. [ ] Implement mapper interfaces for agents, chat sessions, chat messages, knowledge bases, documents, and chunks.
2. [ ] Implement XML mapper files with insert, select, update, delete, and list operations.
3. [ ] Implement recent-message loading for chat sessions.
4. [ ] Implement batch knowledge-base lookup by IDs.
5. [ ] Implement chunk similarity search with `embedding <-> #{vectorLiteral}::vector`.
6. [ ] Preserve list ordering: agents by `created_at DESC`, knowledge bases and documents by `updated_at DESC`, chat messages by `created_at ASC`, and loaded memory messages by `created_at ASC LIMIT {messageLength}`.

Acceptance criteria:

- CRUD operations work against PostgreSQL.
- Similarity search returns at most 3 chunks for a knowledge base.

## Phase 7: Facade Services and REST APIs

1. [ ] Implement facade service interfaces and implementations for agents, chat sessions, chat messages, knowledge bases, documents, tools, SSE, RAG, email, Markdown parsing, and document storage.
2. [ ] Implement all REST controllers and exact endpoint paths from `tech-stack.md`.
3. [ ] Wrap all controller responses with `ApiResponse`.
4. [ ] Publish a chat event whenever a message is created through the public create-message path.
5. [ ] Keep agent-created messages from publishing a new chat event.
6. [ ] Implement diagnostic endpoints `GET /health` and `GET /sse-test` returning raw text `ok`.

Acceptance criteria:

- Every endpoint listed in `tech-stack.md` exists.
- Creating a message through the public create-message path persists it and emits a Spring application event.
- The frontend API wrapper can call implemented endpoints without changing request paths or response shapes.

## Phase 8: Markdown Ingestion and RAG

1. [ ] Implement local document storage under `./data/documents/{kbId}/{documentId}/{uuid}.{extension}`.
2. [ ] On document upload, create the document row before saving the file.
3. [ ] Store the relative file path in document metadata.
4. [ ] For Markdown files, parse sections using flexmark and the exact sectioning rule from `tech-stack.md`.
5. [ ] Generate embeddings from section titles by calling local Ollama `bge-m3`.
6. [ ] Insert one `chunk_bge_m3` row per parsed section with non-empty title.
7. [ ] Implement query-time embedding and pgvector similarity search.

Acceptance criteria:

- Uploading a Markdown file creates a document row and chunk rows.
- Knowledge retrieval returns text from the most similar chunks.

## Phase 9: Tool System

1. [ ] Implement the local `Tool` interface and `ToolType` enum.
2. [ ] Implement fixed tools: knowledge retrieval, terminate, city, date, and weather.
3. [ ] Implement optional tools: read-only database query and async email sending.
4. [ ] Implement `ToolFacadeService` to return all tools, fixed tools, and optional tools.
5. [ ] Convert selected tools to Spring AI callbacks with `MethodToolCallbackProvider`.
6. [ ] Ensure the read-only database query tool can query the seeded sample e-commerce tables.

Acceptance criteria:

- `GET /api/tools` returns only optional tools.
- Every agent runtime receives all fixed tools and only its configured optional tools.

## Phase 10: Chat Model Registry

1. [ ] Create `ChatClient` beans named `deepseek-chat` and `gpt-5.4-mini`.
2. [ ] Back them with `DeepSeekChatModel` and `OpenAiChatModel`.
3. [ ] Implement `ChatClientRegistry` as a map-backed lookup by model name.
4. [ ] Ensure agent configuration stores model names matching registered bean names.

Acceptance criteria:

- An agent configured with `deepseek-chat` or `gpt-5.4-mini` resolves a `ChatClient`.
- Unknown model names fail clearly.

## Phase 11: Agent Runtime

1. [ ] Implement the agent factory.
2. [ ] Load the agent row by ID.
3. [ ] Convert the agent entity to runtime configuration.
4. [ ] Load recent chat messages based on `chatOptions.messageLength`.
5. [ ] Reconstruct Spring AI memory messages for user, assistant, tool, and system roles.
6. [ ] Resolve allowed knowledge bases.
7. [ ] Resolve fixed and optional tools.
8. [ ] Build the agent runtime.
9. [ ] Implement the Think-Execute loop with maximum 20 steps.
10. [ ] Disable Spring AI internal tool execution.
11. [ ] Store `temperature` and `topP` in the agent configuration without applying them to runtime Spring AI `ChatOptions`.
12. [ ] Persist assistant and tool messages.
13. [ ] Push generated messages over SSE after persistence.
14. [ ] Stop when there are no tool calls, when `terminate` is called, when max steps is reached, or when an error occurs.

Acceptance criteria:

- A user message starts an asynchronous agent run.
- The agent can call tools, persist tool results, continue reasoning, and finish.
- Generated messages appear in both the database and the active SSE client.

## Phase 12: SSE

1. [ ] Implement an SSE service with a concurrent map of `chatSessionId` to `SseEmitter`.
2. [ ] Use a 30 minute emitter timeout.
3. [ ] Send an `init` event with data `connected` on connect.
4. [ ] Send JSON `message` events for generated AI content.
5. [ ] Remove clients on completion, timeout, and error.
6. [ ] Expose `/sse/connect/{chatSessionId}` as `text/event-stream`.
7. [ ] Define SSE message types `AI_GENERATED_CONTENT`, `AI_PLANNING`, `AI_THINKING`, `AI_EXECUTING`, and `AI_DONE`.
8. [ ] Emit `AI_GENERATED_CONTENT` from the primary agent runtime for persisted assistant and tool messages.

Acceptance criteria:

- The frontend can connect to a chat session stream.
- Generated assistant and tool messages are received without refreshing the page.

## Phase 13: Frontend Features

1. [ ] Implement the main layout, sidebar, content area, and side menu.
2. [ ] Implement tabs for agents, chats, and knowledge bases.
3. [ ] Implement hooks for agents, chat sessions, documents, and knowledge bases.
4. [ ] Implement context for chat sessions.
5. [ ] Implement modals to add agents and knowledge bases.
6. [ ] Implement agent management UI.
7. [ ] Implement knowledge-base management UI with document upload and document listing.
8. [ ] Implement chat view with empty state, message history, and input.
9. [ ] Create a chat session when a message is sent without an active session.
10. [ ] Create a user message when a message is sent in an active session.
11. [ ] Open an `EventSource` for the active chat session.
12. [ ] Append `AI_GENERATED_CONTENT` messages from SSE to chat history.

Acceptance criteria:

- A user can configure an agent, create a knowledge base, upload Markdown, start a chat, send a message, and see generated messages appear live.

## Phase 14: Verification

1. [ ] Start PostgreSQL with Docker Compose.
2. [ ] Start Ollama locally and ensure `bge-m3` is available.
3. [ ] Start the backend on port `8080`.
4. [ ] Start the frontend with Vite.
5. [ ] Create a knowledge base.
6. [ ] Upload a Markdown document.
7. [ ] Confirm document and chunk rows exist.
8. [ ] Create an agent with a registered model, at least one allowed knowledge base, and optional tools if needed.
9. [ ] Create a chat session.
10. [ ] Connect to SSE.
11. [ ] Send a user message.
12. [ ] Confirm assistant and tool messages are persisted and streamed.

Acceptance criteria:

- The final running system matches the feature set defined by this constitution.
- No extra features are required.
- No technology substitutions have been made.
