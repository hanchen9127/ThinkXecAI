# ADR 054: Clicking an agent opens its profile

- **Status:** Accepted, 2026-11-02

## Context

The starting design sent a click on an agent straight to an empty chat with that agent chosen, and the specs routed `/agent` to the chat view. An agent's setup (model, tools, knowledge bases, how many messages it remembers, its instructions) was visible only inside the edit form. When the agent-management screen was designed element by element, the owner chose to see that setup first.

## Options considered

- **Start a new chat with the agent** (the starting design): the shortest path to a conversation; the setup stays hidden.
- **Open the agent's profile**, with a button to start a chat: one more click, but the setup is readable without opening a form.
- **List the agent's chats**, with a button for a new one: good for returning to earlier work, using the existing by-agent session endpoint.

## Decision

Clicking an agent opens `/agent/:agentId`, a profile of its setup with Start chat, Edit, and Delete in its header. Knowledge bases are shown by name; temperature and top P are marked as saved but not yet used at run time, since the runtime does not apply them. `/agent` without an id asks to choose (or, from commit 6b, create) an agent. Start chat goes to `/chat?agent=<agentId>`: a query parameter rather than router state, so the chosen agent survives a refresh. `/` and `/chat` still open a new chat.

There is no endpoint for a single agent, so the profile reads the shared agent list. Agents and knowledge bases became React contexts like chat sessions, one request each for the whole app.

## Trade-offs

- One more click from an agent to a conversation.
- A deleted or unknown agent id shows "Agent not found" only after the list has loaded.
- If the agent list grows large, a single-agent endpoint would avoid loading every agent for one profile; not needed at the current scale.
