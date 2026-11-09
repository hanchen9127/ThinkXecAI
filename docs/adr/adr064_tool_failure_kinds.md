# ADR 064: Every tool result records whether and why it failed

- **Status:** Accepted, 2026-11-08 (amends 041)

## Context

A tool call that fails is told to the model as text, so the model can correct itself (ADR 041): "Unknown tool: …", "Tool <name> failed: …", or a tool's own text such as "Not sent: … is not an allowed recipient." or "Only SELECT statements are allowed.". The stored tool message held only that text, so the chat showed a failed call exactly like a successful one, and nothing could count failures later without matching sentences. Found by the design critique of 2026-11-06 (25/40).

## Options considered

- **Where failure is known:** a field written by the backend; the frontend matching known text prefixes, as it does for run notices.
- **Shape:** a nullable kind; a boolean `failed`.
- **What counts:** only thrown errors and unknown tools; also a tool's refusals and its other non-success answers.
- **Older messages:** not filled in; filled in once by SQL from the prefixes; a frontend fallback on the prefixes.

## Decision

`ToolResponseMetadata` gets a nullable `failure`: `null` when the call did what was asked, otherwise one of

- `UNKNOWN_TOOL`: the name is not one of the run's tools.
- `INVALID_ARGUMENTS`: the model's arguments are wrong: not JSON, missing or blank fields, more than one address, or SQL the database rejects as malformed (SQLSTATE class 42, such as a syntax error or an unknown column) or for its own values (class 22, such as `'abc'::int` or a division by zero; amended 2026-11-09).
- `REFUSED`: a rule declined the call: not a single `SELECT`, a write the read-only transaction stopped (SQLSTATE 25006), a privilege the database user lacks (42501), a recipient outside the allowlist, a knowledge base the agent may not search, `terminate` in a reply without text (ADR 045).
- `TOOL_ERROR`: the tool or a service behind it failed: an exception, a timeout, Gmail unreachable, rejected, or not configured, any other database error.

The kind is set where the outcome is known: the runtime's resolver for unknown tools, and one failure type that a tool throws with its kind and its text. The runtime's tool callbacks record each call's kind in call order, and the runtime pairs them with the results by position before saving. The model sees the same text as before. "No matching content" is a successful answer, not a failure.

The owner chose the backend over prefix matching so that failures can be counted later, for evaluation by tool, model, and agent (`metadata->'toolResponse'->>'failure'`), without depending on sentences that may be reworded. It also keeps the frontend free of backend wording, in line with handling failures once and generically. Four kinds separate the model's mistakes (`UNKNOWN_TOOL`, `INVALID_ARGUMENTS`) from rules working as intended (`REFUSED`) and from the system's own failures (`TOOL_ERROR`). A new kind can be added without a new field.

The project has not been released, so older tool messages are not filled in: a missing `failure` reads as success, and the frontend does not match text.

**Amended 2026-11-09 (PR #21 review).** Class 22 joins class 42 as `INVALID_ARGUMENTS`: a bad literal or a division by zero is the model's SQL, not the database failing. The statement count no longer reads a parse or connection error as zero statements, which refused the call as "more than one statement"; the error is classified like any other. The driver does not reject unclosed quotes or comments, so those reach the database as one statement and come back as class 42.

## Trade-offs

- The value lives in the JSONB `metadata`, so the database does not check it; the enum in the code and the API description does.
- Pairing kinds with results by position relies on Spring AI executing a reply's calls in order, one at a time; a test pins it, and a change in Spring AI would show there.
- Configuration problems such as Gmail not configured count as `TOOL_ERROR`, together with real tool bugs; split them into a kind of their own if the statistics need it.
- Older test chats show their failed calls as successes.
