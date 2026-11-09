# ADR 017: Only user messages start an agent run, checked before saving

- **Status:** Accepted, 2026-10-08

## Context

Saving a chat message through the API publishes an event that starts the agent in the background. In the starting design every saved message did this, whatever its role, and the client said which agent to run. A message saved as `assistant` or `tool` could therefore start a run, a client could name any agent, and a message sent to a session whose agent had been deleted started a run that failed silently in the background. An empty user message also started a run, although the agent's memory skips empty messages, so the agent answered the previous question instead.

## Options considered

- **Keep the starting design** and let the background run deal with bad input: failures happen where the user cannot see them.
- **Check everything in the request before saving**: reject what cannot lead to a useful run with a clear error code, and start runs only for real user questions.

## Decision

Only messages with role `user` start a run. The agent is looked up from the session on the server; the request has no agent field. Before anything is saved, the request is rejected with code 400 if a user message has no text, with 404 if the session does not exist, and with 409 if the session has no agent any more. Messages with other roles are saved without starting a run. The event carries only the agent and session ids; the agent reads the message itself from the database. Tests record the published events and check every case.

## Trade-offs

- A session whose agent was deleted cannot continue; the user starts a new one.
- The background listener (built with the agent runtime) must run only after the saving transaction commits, or it might not see the new message yet. That part lands in Phase 11.
