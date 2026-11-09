# ADR 016: A chat session's agent is fixed when it is created

- **Status:** Accepted, 2026-10-08

## Context

A chat session belongs to an agent, which decides the system prompt, the tools, and the knowledge bases used in that conversation. The question was whether updating a session may move it to a different agent. A session's history contains tool calls and their results (for example database queries or knowledge-base lookups), which only make sense with the tools and permissions of the agent that made them.

## Options considered

- **Allow changing the agent** (checking that the new one exists): sessions left without an agent after their agent was deleted could be reused, but the new agent would remember tool calls to tools it does not have. Models can misread such a history, and some providers reject it.
- **Only the title can change** (the starting design): the update request has no agent field, and the service keeps the stored agent explicitly.

## Decision

Only the title can change. The agent is chosen when the session is created and never changes afterwards. The update path never writes the agent column at all, so an agent deleted at the same moment cannot cause a database error. Listing the sessions of an unknown agent returns code 404.

## Trade-offs

- A session whose agent was deleted stays readable but cannot continue (a new message gets code 409). To keep chatting, the user starts a new session.
- Moving a conversation to another agent would need a deliberate feature (for example copying only the plain user and assistant messages into a new session), not a field update.
