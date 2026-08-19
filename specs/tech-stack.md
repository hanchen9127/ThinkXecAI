# ThinkXecAI Tech Stack

## Repository Shape

- `backend/`: Spring Boot application.
- `ui/`: React and Vite frontend.
- `db/init/`: PostgreSQL initialization SQL.
- `docker-compose.yml`: PostgreSQL with pgvector.
- `specs/`: project constitution files.

Use module names that match the repository structure in this spec. The technologies, responsibilities, contracts, and runtime behavior must stay the same.

## Backend

- Language: Java 17.
- Framework: Spring Boot `3.5.8`.
- AI framework: Spring AI BOM `1.1.0`.
- Build tool: Maven with Spring Boot Maven Plugin.
- Core starter: `spring-boot-starter`.
- Web stack: `spring-boot-starter-web`.
- Development runtime: `spring-boot-devtools` with runtime scope and optional enabled.
- Data access: JDBC plus MyBatis `3.0.3`.
- Database driver: PostgreSQL JDBC driver.
- Markdown parser: `com.vladsch.flexmark:flexmark-all:0.64.8`.
- Chat model starters: `spring-ai-starter-model-deepseek` and `spring-ai-starter-model-openai`.
- Mail: `spring-boot-starter-mail`.
- Lombok: `1.18.32`.
- Test dependency: `spring-boot-starter-test`.

## Backend Architecture

- Use layered Spring components: `controller`, `service`, `service.impl`, `mapper`, `converter`, `model`, `config`, `agent`, `event`, `exception`, and `typehandler`.
- Controllers expose REST endpoints and wrap responses in `ApiResponse<T>`.
- Facade services implement business workflows and call MyBatis mappers.
- Converters translate between request objects, DTOs, entities, and VOs.
- MyBatis mapper interfaces and XML files own SQL.
- JSON fields are stored as PostgreSQL `JSONB` strings and converted in Java converters.
- Vector fields use a custom MyBatis type handler for `float[]`.
- Domain exceptions use `BizException`; global errors are handled by a REST controller advice.

## Chat Models

- Use Spring AI `ChatClient`.
- Provide a registry component that maps model names to `ChatClient` beans.
- Register exactly two chat clients:
  - Bean name `deepseek-chat`, backed by `DeepSeekChatModel`.
  - Bean name `gpt-5.4-mini`, backed by `OpenAiChatModel` with model option `gpt-5.4-mini`.
- OpenAI configuration is required for the `gpt-5.4-mini` chat client.
- No other chat models are selectable unless a future spec adds them.

## Agent Runtime

- Implement the agent runtime defined in these specs.
- Runtime state values are exactly `IDLE`, `PLANNING`, `THINKING`, `EXECUTING`, `FINISHED`, and `ERROR`.
- The runtime uses `IDLE`, `PLANNING`, `THINKING`, `EXECUTING`, `FINISHED`, and `ERROR` as a shared state contract.
- Maximum loop count: `20`.
- Default chat memory window: `20` messages.
- Agent chat option defaults are `temperature = 0.7`, `topP = 1.0`, and `messageLength = 10`.
- `messageLength` controls how many persisted messages are loaded into memory.
- `temperature` and `topP` are stored in the agent configuration but are not applied to the runtime Spring AI `ChatOptions` in this target state.
- Use `MessageWindowChatMemory`.
- Disable Spring AI internal tool execution with `DefaultToolCallingChatOptions.internalToolExecutionEnabled(false)`.
- Use `ToolCallingManager` to manually execute tool calls.
- Persist `AssistantMessage` as role `ASSISTANT`.
- Persist each `ToolResponseMessage.ToolResponse` as role `TOOL`.
- Do not persist the system prompt as a generated runtime message.
- After persistence, convert generated messages to VOs and push them over SSE.

## Tools

- Every tool implements a local `Tool` interface with `getName()`, `getDescription()`, and `getType()`.
- Tool types are exactly `FIXED` and `OPTIONAL`.
- Convert runtime tools to Spring AI callbacks with `MethodToolCallbackProvider`.
- Fixed tools:
  - `KnowledgeTool`: exposes Spring AI tool method `KnowledgeTool` through `knowledgeQuery(String kbsId, String query)` and calls RAG similarity search.
  - `terminate`: exposes Spring AI tool method `terminate` and ends the agent loop when called.
  - `cityTool`: exposes Spring AI tool method `getCity` and returns the city as a fixed test value.
  - `dateTool`: exposes Spring AI tool method `getDate` and returns the date as `yyyy-MM-dd`.
  - `weatherTool`: exposes Spring AI tool method `weather` and returns a mocked weather result.
- Optional tools:
  - `dataBaseTool`: exposes Spring AI tool method `databaseQuery`, accepts SQL, and executes only statements whose trimmed uppercase text starts with `SELECT`.
  - `emailTool`: exposes Spring AI tool method `sendEmail`, validates `to`, `subject`, and `content`, then sends QQ email asynchronously.
- The database query tool uses the same PostgreSQL database connection as the application and can inspect the seeded sample e-commerce tables.
- `FileSystemTools` and `DirectAnswerTool` remain unregistered unless a future spec changes that.

## RAG and Markdown

- Embedding provider: local Ollama endpoint `http://localhost:11434/api/embeddings`.
- Embedding model: `bge-m3`.
- Embedding vector size: `1024`.
- Markdown parser: flexmark.
- Build the flexmark parser with a default empty `MutableDataSet`; do not enable additional parser extensions unless a future spec changes this.
- Markdown sectioning rule: iterate direct top-level document nodes; every heading starts a section; collect content until the next heading of any level.
- Table rule: if flexmark yields a `TableBlock`, preserve the original Markdown table text when node offsets allow it; otherwise fall back to plain text extraction.
- Chunk creation rule: one chunk per parsed section with a non-empty title.
- Embedding input during ingestion: section title.
- Chunk content: section content, or empty string when content is null.
- Similarity search: embed query text, convert vector to PostgreSQL vector literal, order by `embedding <-> queryVector`, limit `3`.

## Database

- Database: PostgreSQL exposed on host port `5433`.
- Docker image: `pgvector/pgvector:pg18`.
- Database name: `thinkxecai`.
- Username: `postgres`.
- Extension: `vector`.
- Tables:
  - `agent`
  - `chat_session`
  - `chat_message`
  - `knowledge_base`
  - `document`
  - `chunk_bge_m3`
- Seeded sample tables for the optional database query tool:
  - `t_app_user`
  - `t_role`
  - `t_user_role`
  - `t_product_category`
  - `t_product`
  - `t_product_category_relation`
  - `t_order_header`
  - `t_order_item`
  - `t_payment`
  - `t_shipment`
  - `t_comment_topic`
  - `t_comment`
  - `t_comment_topic_mapping`
  - `t_comment_summary_daily`
  - `t_system_kv`
- Primary keys: UUID with `gen_random_uuid()`.
- Foreign keys:
  - `chat_session.agent_id` references `agent(id)` with `ON DELETE SET NULL`.
  - `chat_message.session_id` references `chat_session(id)` with `ON DELETE CASCADE`.
  - `document.kb_id` references `knowledge_base(id)` with `ON DELETE CASCADE`.
  - `chunk_bge_m3.kb_id` references `knowledge_base(id)` with `ON DELETE CASCADE`.
  - `chunk_bge_m3.doc_id` references `document(id)` with `ON DELETE CASCADE`.
- Vector index: `ivfflat` on `chunk_bge_m3.embedding` using `vector_l2_ops` with `lists = 100`.

## Backend REST and SSE Contracts

All REST endpoints that return JSON use this response envelope:

- Success with data: `{ "code": 200, "message": "success", "data": ... }`.
- Success without data: `{ "code": 200, "message": "success", "data": null }`.
- Error: `{ "code": 500, "message": "...", "data": null }`.

Base REST path: `/api`.

- `GET /agents`
- `POST /agents`
- `PATCH /agents/{agentId}`
- `DELETE /agents/{agentId}`
- `GET /chat-sessions`
- `GET /chat-sessions/{chatSessionId}`
- `GET /chat-sessions/agent/{agentId}`
- `POST /chat-sessions`
- `PATCH /chat-sessions/{chatSessionId}`
- `DELETE /chat-sessions/{chatSessionId}`
- `GET /chat-messages/session/{sessionId}`
- `POST /chat-messages`
- `PATCH /chat-messages/{chatMessageId}`
- `DELETE /chat-messages/{chatMessageId}`
- `GET /knowledge-bases`
- `POST /knowledge-bases`
- `PATCH /knowledge-bases/{knowledgeBaseId}`
- `DELETE /knowledge-bases/{knowledgeBaseId}`
- `GET /documents`
- `GET /documents/kb/{kbId}`
- `POST /documents`
- `POST /documents/upload`
- `PATCH /documents/{documentId}`
- `DELETE /documents/{documentId}`
- `GET /tools`

Diagnostic endpoints:

- `GET /health` returns raw text `ok`.
- `GET /sse-test` returns raw text `ok`.

Chat message role values are exactly `user`, `assistant`, `system`, and `tool`.

SSE path:

- `GET /sse/connect/{chatSessionId}` with `text/event-stream`.
- Send an `init` event with data `connected` immediately after connection.
- Send generated messages as event name `message` with JSON data.
- Track clients by `chatSessionId`.
- Use a 30 minute `SseEmitter` timeout.
- SSE JSON shape is `{ "type": "...", "payload": { "message": ..., "statusText": ..., "done": ... }, "metadata": { "chatMessageId": "..." } }`.
- SSE message types are exactly `AI_GENERATED_CONTENT`, `AI_PLANNING`, `AI_THINKING`, `AI_EXECUTING`, and `AI_DONE`.
- The primary agent runtime emits `AI_GENERATED_CONTENT`; the frontend and message contract also include `AI_PLANNING`, `AI_THINKING`, `AI_EXECUTING`, and `AI_DONE`.

## Frontend

- Language: TypeScript.
- Runtime library: React `19.2.0`.
- Build tool: Vite via `rolldown-vite` `7.2.5`.
- UI library: Ant Design `6.0.0`.
- Chat UI helpers: `@ant-design/x` `2.0.0`.
- Markdown rendering: `@ant-design/x-markdown` `2.0.0`.
- Icons: `@ant-design/icons` `6.1.0`.
- DOM renderer: `react-dom` `19.2.0`.
- Routing: `react-router-dom` `7.9.6`.
- Context menu dependency: `react-contexify` `6.0.0`.
- Emoji picker dependency: `emoji-picker-element` `1.28.0`.
- Styling: Tailwind CSS `4.1.17`.
- Linting: ESLint `9.39.1`.
- Formatting dependency: Prettier `3.6.2`.
- TypeScript: `typescript` `~5.9.3`.
- Vite React plugin: `@vitejs/plugin-react` `5.1.1`.
- Tailwind Vite plugin: `@tailwindcss/vite` `4.1.17`.
- API base URL: `http://localhost:8080/api`.
- SSE URL: `http://localhost:8080/sse/connect/{chatSessionId}`.
- Agent model options are exactly `deepseek-chat` and `gpt-5.4-mini`.

## Frontend Architecture

- Keep `src/api/http.ts` as the shared fetch wrapper.
- Keep `src/api/api.ts` as typed API functions and request/response types.
- Keep feature hooks for agents, chat sessions, documents, and knowledge bases.
- Keep chat session state in a React context.
- Keep layout components separate from view components.
- Keep modal components for creating agents and knowledge bases.
- Routes are `/`, `/agent`, `/chat`, `/chat/:chatSessionId`, `/knowledge-base`, and `/knowledge-base/:knowledgeBaseId`.
- Agent chat view must:
  - Load messages by session.
  - Load the session to determine `agentId`.
  - Create a new chat session when no `chatSessionId` exists.
  - Create a user message through `POST /chat-messages`.
  - Open an `EventSource` for the active chat session.
  - Append `AI_GENERATED_CONTENT` messages from SSE to local message state.

## Configuration

- Backend listens on the Spring Boot default port `8080`.
- Frontend runs on the Vite default development port unless changed by Vite.
- PostgreSQL is started by Docker Compose.
- Ollama must be running locally and must have the `bge-m3` model available.
- CORS allows `http://localhost:*` and `http://127.0.0.1:*`, supports credentials, allows `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, and `OPTIONS`, allows all headers, and uses a max age of `3600`.
- Async execution uses a `ThreadPoolTaskExecutor` with core pool size `4`, max pool size `10`, queue capacity `100`, and thread name prefix `async-event-`.

## Secrets

- Commit `application.yaml`; it must not contain real API keys, passwords, tokens, or mailbox authorization codes.
- Load all secrets from a local `.env` file that is never committed to git.
- Commit `.env.example` with every required variable name and only empty or placeholder values.
- Add `.env` to `.gitignore`.
- The backend must read required secrets from environment variables through Spring Boot property placeholders.
- The application must fail fast when any required secret is missing.
- Required secret variables:
  - `THINKXECAI_DB_PASSWORD`
  - `THINKXECAI_MAIL_USERNAME`
  - `THINKXECAI_MAIL_PASSWORD`
  - `THINKXECAI_OPENAI_API_KEY`
  - `THINKXECAI_DEEPSEEK_API_KEY`
- Non-secret runtime configuration may remain in `application.yaml`, including datasource host, datasource port, datasource database name, model names, base URLs, MyBatis settings, and document storage path.
