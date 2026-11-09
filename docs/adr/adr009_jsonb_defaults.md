# ADR 009: Agent settings in JSON columns fall back to database defaults

- **Status:** Accepted, 2026-10-04

## Context

An agent's allowed tools, allowed knowledge bases and chat options are stored in PostgreSQL JSON (`jsonb`) columns. The columns are required and have defaults: empty lists, and the default chat options. The question is what the data layer should do when one of these values is missing on insert. Writing an explicit `NULL` would fail, because the columns are required.

## Options considered

- **Require callers to always fill every value**: the data layer then breaks if any caller forgets.
- **Repeat the defaults in the SQL** (`COALESCE(value, '[]')`): works, but the defaults then live in two places and can drift apart.
- **Leave a missing value out of the INSERT** so the database applies its own column default.

## Decision

Missing values are left out of the INSERT, so the database's column default applies. The defaults are defined once, in the schema. The same approach as timestamps (ADR 005): the database owns its defaults. Tests confirm that an agent created without these values gets exactly the schema defaults, and that a mistake such as always writing the column is caught (in the data layer, and since PR #14 also in the converter).

## Trade-offs

- The INSERT statement is built dynamically, which is slightly harder to read than a fixed one.
- PostgreSQL stores JSON in a normalized form (spacing and key order may change), so the data layer returns equivalent JSON rather than the exact input text. Code and tests compare JSON by content, never as raw text.
