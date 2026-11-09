# ADR 031: The chat-message API takes only user messages, and messages cannot be edited

- **Status:** Accepted, 2026-10-17 (amends ADR 017)

## Context

The create-message endpoint accepted any role and any tool-call metadata from the client, and a PATCH endpoint could change a stored message's text and metadata. Nothing checked that metadata. A review of the merged code found that a client could store tool calls without ids, a tool answer on a user message, or an empty user message (through PATCH, skipping the create rule), and the API then returned `"id": null` although its published description promises a text id. The agent runtime rebuilds its memory from these rows and pairs each tool answer with the tool call it belongs to by id, so such rows would break or mislead the next run.

## Options considered

- **Validate metadata per role:** user and system messages without metadata, assistant messages with complete tool calls, tool messages with exactly one complete answer, and the same rules on PATCH. Correct, but it keeps a public way to write fake model output into the agent's memory, and every rule has to be kept in step with the runtime.
- **Require only non-blank fields:** the smallest change, but a user message could still carry a tool answer.
- **Take only user messages from the API:** the client sends text, the server stores it as a user message; tool calls, tool answers, and the other roles are written only by the agent runtime.

## Decision

The third option. `POST /chat-messages` accepts `{ sessionId, content }`, stores a `user` message, and starts an agent run; `content` must not be blank (400), the session must exist (404) and still have an agent (409), as in ADR 017. `role` and `metadata` are no longer part of the request. `PATCH /chat-messages/{id}` is removed: editing a past message would make the history differ from what the agent saw when it answered, and the user interface has no edit feature. Reading and deleting messages are unchanged. Every stored tool call and answer now comes from a model call made by the runtime, which gives each one a non-blank id.

## Trade-offs

- An API change: a client that sent `role` or `metadata` now has them ignored (unknown request fields are ignored, as elsewhere in the API), and a PATCH gets 400 (method not supported). The generated frontend types change with it; the frontend had no caller yet.
- Tests and tools can no longer create assistant or tool messages through the API; tests write them with the data layer instead.
- Removing the request field exposed a fault in the generated API description: with no request using the metadata type, the generator marked the shared type itself as `null`, and the frontend type became `null`. The description now never marks a named object type as null (only fields that refer to it), and checks on both sides pin the metadata shape.
- A typo in a sent message cannot be fixed; deleting it is still possible.
