# ADR 015: Agent chat options are merged on update and range-checked

- **Status:** Accepted, 2026-10-07

## Context

Each agent has three chat options: `temperature`, `topP`, and `messageLength` (how many recent messages the agent remembers). The frontend may send only some of them. Previously a partial object silently reset the missing ones to their defaults, and no values were checked, so `messageLength: 0` would only fail later, when the agent loads its memory.

## Options considered

- **Replace the whole object on update**: simplest, but changing only `temperature` would quietly reset a custom `messageLength` back to 10.
- **Merge field by field on update**: only the fields sent change, like every other field of an update request.
- **Range checks**: none (fail at runtime), only `messageLength` (the one used today), or all three.

## Decision

On update, chat options are merged field by field; on create, missing fields take the defaults (0.7 / 1.0 / 10). To tell "not sent" from "sent the default value", the options class no longer has built-in defaults; they live in one `defaults()` method, and a test checks that they match the database column default. All three values are range-checked and rejected with code 400: `temperature` 0–2, `topP` 0–1, `messageLength` 1–100.

## Trade-offs

- An explicit `null` is treated as "not sent", so a client cannot reset one field to its default except by sending the default value.
- The defaults exist in Java and in the database schema; the test above keeps them from drifting apart.
- `temperature` and `topP` are stored but not yet used by the agent. Their ranges follow what the supported model providers accept, and should be checked again if a provider with different limits is added.
