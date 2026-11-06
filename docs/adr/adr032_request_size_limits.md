# ADR 032: Request fields have size limits, and the knowledge-base lookup binds one array

- **Status:** Accepted, 2026-10-17

## Context

No request field had a maximum size, and every text column is `TEXT`. A client could store megabytes in an agent name or a system prompt; once the agent runtime exists, the system prompt and message text are sent to paid models on every run. The agent's `allowedKbs` list was checked with one query parameter per id, and the database driver refuses a statement with more than 65,535 parameters, so a long list returned a generic server error (500) instead of a client error (400). A review of the merged code found both.

## Options considered

- **Limit only the two lists** (and bind them differently): removes the server error, leaves the text fields open.
- **Database column limits** (`VARCHAR(n)`): the error would come from the database as a server error unless mapped back, and the schema only runs on a new database volume.
- **Bean Validation limits on every request field**, plus one array parameter for the lookup.

## Decision

The third option. Names and titles (agent, knowledge base, document, chat session) are at most 200 characters, descriptions at most 2,000, the agent system prompt and chat-message content at most 20,000, and `allowedTools` / `allowedKbs` at most 50 entries each. The lists are counted as sent, before duplicates are dropped. An uploaded file's name (without the client's path) is at most 200 characters, since it becomes the document name. Anything longer is refused with 400 and the standard message `<field>: size must be between 0 and <max>`. The limits live in one class and appear in the generated API description (`maxLength`, `maxItems`). The knowledge-base lookup now binds all ids as one PostgreSQL `uuid[]` parameter (`id = ANY(?)`), so the statement does not grow with the list. Tests check every limit at the maximum and one above it, the HTTP message for a list, an upload name, and a lookup with 70,000 ids.

## Trade-offs

- The numbers are judgment calls: generous for a personal assistant, small enough to keep one request well under a megabyte of text. Changing one is a one-line change plus its test.
- Rows stored before the limits are not re-checked; an update of another field still succeeds.
- MyBatis has no array handler that works for UUIDs with the PostgreSQL driver, so the project adds a small one.
