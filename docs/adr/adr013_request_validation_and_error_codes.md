# ADR 013: Request validation and error codes

- **Status:** Accepted, 2026-10-07

## Context

Until now every unexpected exception was returned to the caller with its raw message, so a database error could show SQL, constraint names or Java class names in the response. Invalid input was not recognized either: malformed JSON, an id that is not a UUID or a missing required field all came back as a server error (code 500), so the client could not tell its own mistake from a server failure.

## Options considered

- **Hand-written checks in each service**: no new dependency, but repetitive, easy to forget, and invisible to the generated API description.
- **Bean Validation annotations on request classes** (`spring-boot-starter-validation`, the Spring standard): rules sit next to the fields, and the API description generated later marks those fields as required.
- **Return real HTTP status codes (400, 404, …)** instead of HTTP 200 with the code in the response body: more conventional, but would change the existing API contract that the frontend already relies on.

## Decision

Request bodies are validated with Bean Validation. One central exception handler maps errors to codes inside the existing `{ code, message, data }` envelope (HTTP status stays 200):

- **400**: malformed or missing JSON, a wrong value in the JSON (the message names the field, e.g. `role: invalid value`), a path id that is not a UUID, a failed validation rule, or an unsupported method or content type.
- **404**: an unknown resource or an unknown URL.
- **409**: the request conflicts with the current state.
- **500**: everything else, with the fixed message `Internal server error`. Details go only to the server log.

A business exception without a client error code (such as a stored JSON value that cannot be read) counts as an internal failure and also returns the generic 500. Tests cover each case, and each was checked by breaking the handler on purpose.

## Trade-offs

- All messages are English. The request locale is fixed to English, so validation messages ignore the browser's language (a Chinese browser would otherwise get Chinese messages). The frontend shows them as they are; translating them would be a frontend concern.
- A generic 500 makes debugging from the client side harder; the server log is the place to look.
- Keeping HTTP 200 for errors is unusual and means generic HTTP tools do not see failures. Revisit together with any future API version.
