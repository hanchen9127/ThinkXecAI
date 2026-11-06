# ADR 028: The database enforces the read-only query tool

- **Status:** Accepted, 2026-10-14; amended by [ADR 030](adr030_sql_tool_single_statement_own_connection.md)

## Context

Agents can be given a tool that runs SQL so they can answer questions about the sample shop data. In the starting design the only guard was a check that the text starts with `SELECT`, and the query ran with the application's own connection. `SELECT 1; DROP TABLE agent` passes that check, as does a write hidden in a common table expression, so anything that can steer the model, including text inside an uploaded document, could change or delete data. A slow query could also hold a connection and a worker thread indefinitely, and an unbounded result could flood the model.

## Options considered

- **Better text checks** (reject `;`, parse the SQL): every parser gap becomes a hole, and the database already knows exactly what a statement does.
- **A separate database account limited to the sample tables:** the strongest guard, but it needs a second connection pool, a new secret, and a setup step for existing databases.
- **A read-only transaction per query, with limits:** PostgreSQL itself refuses every write, whatever the text looks like.

## Decision

Each call runs in its own new read-only transaction, so PostgreSQL rejects writes after a `;`, inside a common table expression, and in `SELECT INTO`. The `SELECT` check stays only as a quick, friendly refusal. A query is stopped after 5 seconds by two independent timers: the database's statement timeout and the JDBC driver's query timeout. The second one is needed: a query can switch the database timer off for the statement that follows it (`SELECT set_config('statement_timeout', '0', true); SELECT pg_sleep(600)`), and a test shows exactly that case fails without the driver timer. At most 200 rows are returned, with a note when there were more. Errors go back to the model as the database's own message, never a stack trace. Tests run against the real database and check that no setting is left behind on pooled connections and that a caller's transaction is not affected.

## Trade-offs

- The application connects as a database superuser. Probed in a read-only transaction: superuser functions that do not write still work, such as reading files inside the database container and ending other connections, and the application's own tables are readable. Turning the read-only flag off, or importing files, is refused; the database password is not in the server process environment. This is accepted for now because the project has no user accounts (the application's tables are already readable through its API). A dedicated account limited to the sample tables (the second option) is the v2 fix and should come first if the project ever gets authentication.
- Queries starting with `WITH` are refused by the `SELECT` check, as the specs require, although the database would run them safely.
- Each call opens a short transaction of its own, so the tool cannot see uncommitted data of a caller. The agent loop has none.
