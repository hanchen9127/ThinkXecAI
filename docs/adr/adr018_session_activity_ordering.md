# ADR 018: Sessions are listed by their latest activity

- **Status:** Accepted, 2026-10-08

## Context

Chat sessions are listed newest first by their `updated_at` time, which suggests "most recently active first". In the starting design, however, that time only changed when a session was renamed, not when a message was added, so a conversation in active use could sink to the bottom of the list.

## Options considered

- **Leave it as is**: sessions sort by their last rename, which does not match what users expect.
- **Order by the time of the latest message**: correct, but every list query would need to look into the messages table.
- **Refresh the session's `updated_at` whenever a message is added**, in the same transaction as the message.

## Decision

Adding a message also sets its session's `updated_at` to the current time, in the same database transaction, so either both are saved or neither is. The list queries stay as they are. A dedicated data-layer method changes only that timestamp, and tests check both the method and that adding a message moves the session's time forward.

## Trade-offs

- One extra small update per message.
- `updated_at` now means "last activity" (a message or a rename) rather than "last edit of the session itself".
