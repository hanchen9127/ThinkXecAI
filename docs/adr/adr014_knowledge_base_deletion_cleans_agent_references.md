# ADR 014: Deleting a knowledge base removes it from agents

- **Status:** Accepted, 2026-10-07

## Context

An agent lists the knowledge bases it may search in `allowedKbs`, stored as a JSON array in one database column. A JSON array cannot have a foreign key, so the database cannot clean up or block references when a knowledge base is deleted. Left alone, deleted ids stay in agents' lists, and saving such an agent again fails the rule that every listed knowledge base must exist.

## Options considered

- **Delete and leave the ids behind** (the starting design): the agent runtime ignores unknown ids, but editing the agent later fails validation, and the stored data no longer matches reality.
- **Refuse to delete while an agent uses it** (code 409): safest, but the user must first edit every agent that uses it.
- **Delete it and remove its id from every agent in the same transaction.**
- **Move `allowedKbs` into a join table with real foreign keys**: the cleanest data model, but it changes the schema and the data layer that the specs define.

## Decision

Deleting a knowledge base also removes its id from every agent's `allowedKbs`, in the same database transaction, so either both changes happen or neither does. Its documents and chunks are deleted by the existing foreign-key cascade. Changed agents get a new `updated_at`. Tests check that only agents that referenced it change, and that deleting an unknown knowledge base changes nothing.

## Trade-offs

- Deleting a knowledge base silently changes agents; the user is not asked which agents are affected. A confirmation dialog in the frontend can list them later if needed.
- The cleanup is application code rather than a database rule, so any future code path that deletes knowledge bases must go through the same service.
- A join table would remove the problem entirely; revisit if more rules about agent ↔ knowledge base links appear.
