# ADR 062: The whole application in Docker, on request

- **Status:** Accepted, 2026-11-06

## Context

Docker Compose started only PostgreSQL; the backend and frontend ran on the host, so seeing the application meant two terminals and loading `.env` by hand. The owner wanted to start the application from Docker. CI runs `docker compose up` and expects PostgreSQL alone, and ADR 033 keeps everything on the loopback address because the API has no authentication.

## Options considered

- **How it starts:** a Compose profile, started on request, with production images; containers running Vite and `spring-boot:run` on mounted sources, with hot reload but slow file watching on Windows; always starting all three, which would make CI build the images and occupy ports 8080 and 5173 during development.
- **Ollama:** stay on the host, reached through `host.docker.internal`; a container of its own, which needs the model downloaded again and GPU setup on Docker Desktop.

## Decision

A Compose profile `app` adds `backend` (the jar on a Temurin JRE, not root) and `frontend` (the production build behind Nginx): `docker compose --profile app up -d --build`. A plain `docker compose up` is unchanged. Ollama stays on the host.

ADR 033 still holds, without any code change. The backend listens on all interfaces of its container (`SERVER_ADDRESS`), because a published port cannot reach the container's loopback, and the port is published on the host's `127.0.0.1` only; so is the frontend's. The Host check stays. The browser calls `localhost:8080` itself, so Nginx proxies nothing and SSE is untouched. Other settings are environment overrides in `docker-compose.yml`; `application.yaml` still describes a run on the host.

`backend/data` is mounted, because a container and a host run share one database and so must see the same stored files.

## Trade-offs

- A code change needs `--build`; for daily work the host run with hot reload stays the way to develop.
- The containers use ports 8080 and 5173, so the two ways of running cannot be up at once.
- Ollama must be running on the host first; an upload fails with ADR 057's 503 otherwise.
- Anything on the Compose network can reach the backend past the published port; only these three containers are on it.
- On a Linux host the mounted folder must be writable by user 1000.
