# ADR 027: Optional Gmail settings, synchronous sending with one 10-second deadline

- **Status:** Accepted, 2026-10-14 (refines ADR 012)

## Context

ADR 012 chose the Gmail API with a send-only OAuth token and a recipient allowlist. Three questions remained when the tool was built. Should the application refuse to start without Gmail credentials, although email is an optional tool that most runs never use? Should the tool send in the background, as the starting design did, so the model is told "submitted" and a failure only reaches the log? And how long may a send take, given that the agent waits for the answer?

## Options considered

- **Startup:** require all four Gmail values (every developer must set up Google Cloud first); or make them optional and report what is missing at the first send.
- **Sending:** in the background (fast, but the agent can claim an email was sent when it was not); or synchronously, returning the real outcome.
- **Getting the access token:** Google's auth library refreshes tokens with fixed 20-second connect and read timeouts that cannot be changed, so one send could take about 50 seconds; wrapping it in an outer 10-second limit leaves the request running and can deliver an email after the agent was told it timed out, inviting a duplicate; or the application sends the standard OAuth refresh request itself with the same HTTP client and limits as the send.

## Decision

The Gmail values are optional. Startup never checks them, the tool is always offered, and a send without all four answers with the names of the missing variables (never their values). The allowlist stays fail-closed on its own: an empty list refuses every send.

The tool sends synchronously and returns the real outcome: sent with Gmail's message id, refused, not configured, rejected by Google (for example, a revoked refresh token), or timed out. The agent already runs in the background, so no web request waits.

The application refreshes the access token itself and keeps it until a minute before it expires. The token request and the send share one 10-second deadline. A timeout while getting the token means nothing was sent and says so; a timeout during the send itself says the email may or may not have been sent and should be checked before trying again.

Before anything is sent, the recipient must parse as exactly one email address: lists, groups, and line breaks are refused, a display name is dropped, and the parsed address (not the raw text) is compared with the allowlist, ignoring case. Subjects must be a single line, so no extra mail headers can be injected. Refusals never reveal which addresses are allowed.

## Trade-offs

- A wrong or partial Gmail setup is noticed at the first send, not at startup. The answer names what is missing, so it is quick to fix.
- About thirty lines of token handling are the project's own instead of the library's. They follow the standard OAuth refresh request and are tested against a local fake of Google's endpoints, including slow and failing answers. Google's auth library is still on the classpath through the Gemini SDK; the email tool simply does not use it.
- A send can still time out after Google received it. The answer says so instead of guessing.
