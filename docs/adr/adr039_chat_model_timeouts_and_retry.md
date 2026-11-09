# ADR 039: Chat model calls have timeouts and at most 3 attempts

- **Status:** Accepted, 2026-10-18

## Context

From Phase 11 every agent run calls a chat model up to 20 times on `asyncEventExecutor`, which has 4 core threads. With Spring AI's auto-configuration neither model had a timeout: DeepSeek's `RestClient` had no read timeout, and the google-genai SDK (1.37.0) sets OkHttp's connect, read, and write timeouts to 0. Retries were long: Spring AI's `RetryTemplate` retries DeepSeek's 5xx responses and network errors 10 times, 2 s × 5 apart up to 3 minutes (about 20 minutes in the worst case). Gemini is never retried by that template, because Spring AI's Gemini model wraps every SDK error in a plain `RuntimeException`; the SDK retries by itself instead, 5 attempts on 408, 429, 5xx, and I/O errors, up to 60 s apart. A hung or failing model could therefore hold an agent thread indefinitely or for many minutes. ADR 036 had already given Ollama its own client with timeouts and left the chat models alone.

## Options considered

- **Set timeouts and retries now, on the application's own clients:** the same pattern as Ollama.
- **Decide in Phase 11 with the executor's rejection policy:** the clients are built here, and every Phase 11 test would run on the unbounded clients.
- **Keep the defaults.**

## Decision

The first option. `ChatModelConfig` builds both models, so the auto-configured beans back off. DeepSeek gets its own `DeepSeekChatModel` with a `DeepSeekApi` whose `RestClient` uses a JDK request factory: connect timeout `thinkxecai.chat.connect-timeout` (10 s) and read timeout `thinkxecai.chat.read-timeout` (120 s), per attempt. Gemini gets its own `Client`: the SDK offers only a call timeout, which cancels the request but not the SDK's wait before a retry, so a hanging call ends after the 120 s plus all remaining retry waits (each further attempt fails at once on the canceled call, but the SDK still waits before it: 2 s + 6 s = 8 s with 3 attempts, more if `max-attempts` is raised). Both timeouts must be positive (startup fails otherwise): a zero would switch Gemini's timeout off and fail every DeepSeek call at once. Retries: `spring.ai.retry.max-attempts: 3`, backoff 2 s, multiplier 3, at most 10 s; DeepSeek uses them through the `RetryTemplate` (4xx not retried), and the same values go into the Gemini SDK's retry options with its random jitter turned off, so both models wait 2 s and then 6 s (status codes unchanged: 408, 429, 5xx, and I/O errors). The Gemini base URL is a setting (`thinkxecai.chat.gemini-base-url`) so tests can use a local server. Embeddings are not affected (no retry, ADR 036). Tests drive both beans against a fake server with a 1 s timeout: an answer that comes after 5 s fails the call (Gemini within 3 s, DeepSeek after 3 timed-out attempts), a 503 is tried 3 times, a 400 once; removing the Gemini timeout, the DeepSeek read timeout, the Gemini retry options, the attempt limit, or the positive-timeout check fails a test. Not covered by a test: the DeepSeek connect timeout (a fake server always accepts at once, and an unroutable address behaves differently across networks), the jitter setting (indistinguishable at test backoff values), and the production values themselves (as for Ollama).

## Trade-offs

- A slow but working answer (a long Gemini thinking phase, a busy DeepSeek) above 120 s now fails; the setting can be raised.
- The two models are bounded differently: DeepSeek up to 3 × 120 s plus backoff, Gemini 120 s plus the remaining retry waits (8 s) for everything. Making them equal would need a call timeout around DeepSeek's retries, which Spring AI does not offer.
- Gemini retries 429 (rate limit), DeepSeek does not (Spring AI treats 4xx as final); both are the libraries' defaults.
- The two beans copy the auto-configuration's wiring (properties, tool calling manager, observation); a Spring AI upgrade must check that copy. Not copied: Boot's `WebClient.Builder` for `DeepSeekApi` (used only for streaming, which this project does not do; a streaming call would have no timeout) and the auto-configuration's non-blank checks on the DeepSeek key and base URL (the key is checked at startup by `RequiredSecretsValidator`, and `DeepSeekApi` refuses an empty base URL).
