# ADR 033: The backend and database are reachable only from this machine, and only under a local host name

- **Status:** Accepted, 2026-10-17

## Context

The API has no authentication: whoever can reach it can read and delete every agent, chat, and document, upload files, and, once the agent runtime exists, spend the owner's model credits, run the SQL tool, and send email from the owner's Gmail account. A review of the merged code found that the backend listened on every network interface and that Docker published the PostgreSQL superuser login on every interface too, protected only by its password. Anyone on the same Wi-Fi could use both. Two smaller points: cross-origin requests were allowed "with credentials" although the frontend sends no cookies, and a website could still reach a loopback-only server through DNS rebinding (its own name is made to resolve to `127.0.0.1`, so the browser treats the API as the same site).

## Options considered

- **Record the risk and change nothing:** acceptable only on a trusted network, and easy to forget.
- **Bind to the loopback address only:** closes the network path, but not DNS rebinding.
- **Loopback binding plus a host-name check, and no credentials in CORS.**
- **Add authentication:** the real answer for a shared deployment, but out of scope for a single-user local application.

## Decision

The third option. The backend listens on `127.0.0.1` only, and Docker Compose publishes PostgreSQL on `127.0.0.1:5433` only. A filter in front of every request (API, health check, and SSE) accepts only the host names `localhost` and `127.0.0.1`, with any port; any other `Host` gets HTTP 403 with the usual envelope and code 403, before any controller runs. A rebinding page sends its own name as the host, so it is refused. CORS keeps the local origins but no longer allows credentials. Tests check the bound address of the running server, the refused and accepted host names on a real endpoint, and the CORS headers.

## Trade-offs

- The application cannot be opened from a phone or another computer, even on purpose. Doing that safely needs authentication first; it is not a configuration switch.
- The 403 is a real HTTP status, unlike the API's other errors (HTTP 200 with the code in the body), because the request never reaches the API; the body still uses the envelope.
- `[::1]` (IPv6 loopback) is not accepted: the server is not listening there.
- Tools that connect to the database from outside Docker on another machine stop working; on this machine nothing changes.
