# ADR 030: The SQL tool runs exactly one statement, on a connection of its own

- **Status:** Accepted, 2026-10-16 (amends ADR 028)

## Context

ADR 028 made the SQL tool safe by running each query in a read-only transaction. Review of the pull request found two ways around it, both reproduced against the database. First, the text may hold several statements, and one of them can end the transaction: in `SELECT 1; COMMIT; DROP TABLE agent`, the `DROP` runs in a new transaction that is no longer read-only, and it deleted the test table. Second, the query ran on a connection from the application's shared pool, and a successful read-only transaction is committed. Session settings changed with `set_config(..., false)` (a 1 ms timeout, a different `search_path`) stayed on that connection and broke the application's next requests, and session-level advisory locks survive even a rollback. Results were also limited only in rows, not in size, so one huge value could flood the model or the server's memory.

## Options considered

- **Own text checks for `COMMIT` and similar words:** easy to miss a spelling or a quoting trick, and they would differ from how the driver splits the text.
- **A dedicated database account with read access to the sample tables only:** the strongest guard, also against the superuser functions ADR 028 accepted, but it needs a new secret, a setup script, and a second connection pool. Kept as the v2 step.
- **Inside the application:** allow one statement only, counted by the JDBC driver's own parser; always roll back; and run each call on a connection opened for it and closed afterwards.

## Decision

The third option. The tool counts statements with the PostgreSQL driver's own parser, on the exact text the driver will send: JDBC escape processing is switched off (the driver would otherwise rewrite escapes such as `{fn ...}` before splitting, so the counted and the executed text could differ), and the connection's own string-quoting setting is used. Semicolons in strings and comments do not count; more than one statement is refused before the query runs, and with the extended query protocol the server itself also refuses several commands in one statement. That closes the `COMMIT` path and also the way a first statement could switch the timeout off for a second one. Each call opens its own connection (5-second connect timeout, 10-second read timeout for a silent server or network, and a 16 MB limit on the result the driver will read), runs read-only with the 5-second database and driver timeouts, always rolls back, and closes the connection, which ends any session setting or advisory lock the query created; the application's pooled connections are never handed to the tool. Each value is cut at 1,000 characters and the whole answer at 20,000, with the cut marked. Tests cover every reproduced attack, statements with semicolons inside literals and comments, and that the application's connections are unchanged afterwards.

## Trade-offs

- Opening a connection costs a few tens of milliseconds per call, small next to a model call. The tool's connections are not limited by the pool, but the agent runs at most a few at a time.
- The JDBC driver becomes a compile-time dependency, and the tool relies on its parser class. A driver upgrade that changes it would fail the tests, not pass silently.
- Results over 16 MB are refused by the driver as a failed query, so no single value can fill the server's memory; below that, the 1,000- and 20,000-character cuts protect the model's context.
- JDBC escape syntax (`{fn ...}`, `{d ...}`) is not supported; the model writes plain PostgreSQL SQL anyway.
- The number of tool connections open at the same time is not limited; the agent thread pool keeps it small. Revisit with real load in the agent runtime phase.
- The superuser reach accepted in ADR 028 is unchanged; the dedicated account remains the next step.
