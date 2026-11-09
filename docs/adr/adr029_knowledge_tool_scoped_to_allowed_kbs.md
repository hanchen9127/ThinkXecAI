# ADR 029: Knowledge search limited to the agent's own knowledge bases

- **Status:** Accepted, 2026-10-13

## Context

Each agent lists the knowledge bases it may use. The model searches them through a tool that takes a knowledge-base id and a query. In the starting design that list was only mentioned in the prompt; the tool searched whatever id the model passed. Knowledge-base ids are visible through the API, so a model that invents an id, or is told one by injected text in a document or message, could read a knowledge base the agent was never given. A malformed id reached the database as a raw cast error.

## Options considered

- **Trust the prompt** (starting design): the list is a hint, not a permission.
- **One tool instance per agent**, built with that agent's list: works, but tools are shared Spring beans and every run would rebuild them.
- **Pass the list in Spring AI's tool context:** a per-call map that the runtime sets and the tool reads; it is not part of the arguments the model sees, so the model cannot change it.

## Decision

The runtime passes the agent's allowed ids in the tool context under one key. The tool compares the requested id as a UUID, so case and surrounding spaces do not matter. Any id outside the list, or not a UUID, gets an answer naming the knowledge bases that are available instead of a search, so the model can correct itself; nothing is thrown. A missing list means no knowledge base is allowed. Tests check that the model-facing arguments are only `kbsId` and `query`, and that the context reaches the tool through the same Spring AI path the runtime uses.

While testing, a Spring AI rule surfaced: a tool method that accepts a tool context is refused outright when the call carries no context or an empty one, with an exception the tool-calling loop does not catch. The runtime therefore always passes the key, even for an agent with no knowledge bases. A test pins this behavior, so an upgrade that changes it is noticed.

## Trade-offs

- The answer to a refused id lists the agent's own knowledge-base ids. That reveals nothing the agent was not already given, and it lets the model recover.
- The guarantee depends on the runtime (Phase 11) setting the context on every model call; a runtime test covers it there.
