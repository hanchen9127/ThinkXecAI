# ADR 057: A service that does not answer is a 503 that says to try again

- **Status:** Accepted, 2026-11-03 (amends 013)

## Context

The browser check before the Phase 13 pull request uploaded a Markdown file while Ollama had been idle. Loading the embedding model took longer than the 60-second read timeout (ADR 036), and the upload failed with "Internal server error"; the same upload a minute later took one second. The generic 500 of ADR 013 gave the user no hint that trying again would work, or that Ollama might not be running.

## Options considered

- **A clear error, raised where Ollama is called**: one catch and one message per service; every model or service added later needs its own.
- **A clear error, raised in one place** (chosen): the error handler maps any outbound call that got no answer to one generic message.
- **A longer timeout**: helps a slow model load, not an Ollama that is not running; a hanging call would hold an upload longer.

## Decision

The global error handler maps `ResourceAccessException`, which Spring's HTTP clients throw when a call gets no answer, to code 503 with a message that names no service: a read timeout says the service "did not answer in time; it may still be starting up. Try again in a minute"; a refused connection or a connect timeout says it "could not be reached; check that it is running, then try again". The client's own message, which carries the service's URL, goes only to the log. An answer that is an error (Ollama replying 500, a missing model) is still a generic 500. Nothing in the services knows about it, so a model or service added later through Spring's HTTP clients gets the same answer without code.

Tests: the handler for each failure kind and that no URL leaks; against a fake Ollama, that the real client's read timeout arrives as this exception with a timeout cause, and an error reply does not.

## Trade-offs

- The message does not say which service failed; the log does. Today only the embedding model is called during a request.
- A client not built on Spring's HTTP support (the Gemini SDK's OkHttp, the Gmail client) throws other exceptions; those calls run inside agent runs, not requests, so they never reach this handler.
- The knowledge tool's errors are unchanged: the model still gets the client's message as the tool result.
