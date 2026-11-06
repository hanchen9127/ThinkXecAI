# ADR 040: The chat client registry checks its names and its clients never execute tools

- **Status:** Accepted, 2026-10-18

## Context

The agent runtime (Phase 11) looks up a `ChatClient` by the agent's `model`, then executes tool calls itself: it persists each assistant and tool message and passes the agent's allowed knowledge bases as tool context (ADR 029). Spring AI's default is the opposite: a model that receives tool callbacks runs the returned calls itself and loops until the model stops calling tools, without persisting anything, and `KnowledgeTool` refuses to run without tool context. Agent requests are validated against `ChatModelNames` (ADR 013), so every saved agent names one of the two models; the registry must hold exactly those.

## Options considered

- **Turn off internal tool execution on the registry's clients:** default options `internalToolExecutionEnabled = false` on both clients; the runtime may set it again per request.
- **Turn it off only per request in the runtime:** one call site that forgets it lets the model run tools outside the loop.
- For the registry: check the bean names at startup, or only fail when an unknown name is looked up.

## Decision

Both `ChatClient` beans (named `deepseek-chat` and `gemini-3.8-flash`) are built with `ChatClient.builder(chatModel)` and default options `internalToolExecutionEnabled = false`, and nothing else: an agent's temperature and topP stay unapplied. Options passed with a request replace these defaults in Spring AI 1.1.8 (`DefaultChatClient`), so a request that sets options must set the flag too; the spec says so. `ChatClientRegistry` is built from the `ChatClient` beans by name and fails startup when a name from `ChatModelNames.ALL` is missing or another name is registered. `get(model)` with an unknown or null name throws `IllegalArgumentException` naming the model and the registered names; the runtime will report it as a failed run. A test keeps the request `@Pattern` equal to `ChatModelNames.ALL`. Tests: each registered client reaches its own model on a fake server; with a tool callback registered, both models' tool calls come back to the caller and the tool is never run; a mocked model receives `internalToolExecutionEnabled = false` and no temperature or topP; the registry's name checks. Removing the default options, the startup check, or swapping the models between names fails a test.

## Trade-offs

- Spring AI's auto-configured `ChatClient.Builder` (a prototype bean that needs a single `ChatModel`) is never used; injecting it would fail with two models.
- The registry holds exactly two names; adding a model means changing `ChatModelNames`, the request pattern, and a bean, which the tests tie together.
