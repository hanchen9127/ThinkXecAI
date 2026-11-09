# ADR 038: A Gemini response becomes one assistant message

- **Status:** Accepted, 2026-10-18

## Context

The agent runtime reads one assistant message per model call: its text, and its tool calls to execute. Spring AI 1.1.8's `GoogleGenAiChatModel` treats a Gemini candidate as a tool call only when every part is a function call. Gemini 3 often answers with a sentence and a function call in the same candidate; then the call is dropped, the runtime sees no tool calls, and the run ends early (spring-ai #5466). The same code turns a text answer that Gemini split into several parts into several generations, and `getResult()` returns only the first. The fix on Spring AI's `main` (`775ebaa`) is not in a 1.1.x release; it returns one generation per text part plus one generation carrying all the calls. Gemini 3 also returns thought signatures that must be sent back on the function calls of the current turn; Spring AI reads them from the message metadata and attaches them to the calls in order.

## Options considered

- **Subclass, one generation per candidate:** non-thought text joined, every function call, the calls' signatures. Both models then give the runtime one message, which maps onto one persisted `assistant` row with `toolCalls`.
- **Subclass that copies the upstream fix:** several generations per response. Deleting the subclass after an upgrade would change nothing, but the runtime must merge generations and decide what to persist, for Gemini only.
- **Wait for a release:** the agent cannot use Gemini tools until then.

## Decision

The first option. `ThinkXecGoogleGenAiChatModel` overrides `responseCandidateToGeneration` and returns one generation: text from the parts that are not thoughts, joined without a separator; tool calls from every function-call part in order, with Gemini's call id when present and `""` otherwise (the runtime fills blank ids); metadata `candidateIndex`, `finishReason`, and `thoughtSignatures`. With function calls, `thoughtSignatures` holds only the calls' signatures in order, because Spring AI attaches the stored list to the calls one by one and a text part's signature first would land on the wrong call; without calls it holds every part's signature, as upstream. A candidate without content gives one empty message. Unit tests build candidates in memory: text with one call, parallel calls with a signed text part, calls only, split text, thought parts, call ids, a text-only answer's signatures, no content, finish reason; removing the override, the thought filter, or the signature filter, or keeping only the first text part, each fails a test. A test against a fake server sends the message back and checks the signature lands on the call. A run against the real `gemini-3.8-flash` (2026-10-19, external test) returned exactly this shape for a prompt asking it to say what it will do before calling a tool — one sentence, one function call with an id, one signature — and accepted the round trip.

## Trade-offs

- When the subclass is removed (a release with the upstream fix, most likely Spring AI 2.x), the runtime must merge a response's generations itself; the spec says so.
- Joining text parts without a separator relies on Gemini splitting text at arbitrary points, as it does; parts that were meant as separate paragraphs would run together.
- Thought text is dropped. Thoughts are only returned when `includeThoughts` is set, which this project does not do.
