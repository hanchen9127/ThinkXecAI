# ADR 045: `terminate` ends a run only in a reply that has text

- **Status:** Accepted, 2026-10-20

## Context

The fixed tool `terminate` ends the Think-Execute loop, and the loop also ends when a reply has no tool calls. In the first real Gemini runs (PR #16, 2026-10-20) Gemini answered a multi-step request by calling the tools, then calling `terminate` in a message without any text, twice in a row. The run ended "successfully", but the user saw no answer: tool results are stored, but the frontend shows the assistant's text. DeepSeek, in the same tests, wrote its answer and called `terminate` in the same message. The tool's description said only "Call this once every task the user asked for is complete."

## Options considered

- **Tell the model, and guard in the runtime**: the description and the guidance say the user sees only the model's text; a `terminate` in a reply without text does not end the run.
- **Only tell the model**: smallest change, but nothing makes the model follow it.
- **One extra call without tools** when `terminate` comes without text, asking only for the final answer: always yields text, but adds a second kind of model call to test and maintain.
- **Stop offering `terminate` to the model** and end only on a reply without tool calls: cleanest, but `terminate` is a fixed tool in the mission and the specs.

## Decision

The first option (chosen by the owner). The `terminate` tool description says the user sees only the model's text and that the final answer goes in the message that calls it; the runtime guidance gains principle 4 to the same effect (ADR 041's text). In the runtime, `terminate` ends the run only when the assistant message that calls it has non-blank text. Called in a message without text, its result is replaced by "Not ended: the user sees only your text and has no answer yet. Write your final answer to the user as text." (stored and sent like any tool result, as JSON) and the loop goes on, still within 20 steps. The rule looks at the calling message only, not at earlier text in the run: an earlier "Let me check." is not an answer.

Amended 2026-10-21 (owner): in the real runs Gemini called `terminate` without text again right after the "Not ended" result, so the result alone did not produce an answer. The model call after a refused `terminate` now offers no tools; its reply can only be text, which ends the run (an empty one is a failed model call, ADR 043). Both models answer a history with tool calls and no tools offered (checked against the real APIs: `ChatModelExternalTest#aConversationWithToolCallsCanBeAnsweredWithoutTools`). For comparison, the starting design ended the loop on any `terminate` call and its guidance said nothing about answering in text.

## Trade-offs

- A model that ignores the guidance costs one more step per run, and could in the worst case use up the 20 steps (the max-steps notice then tells the user).
- A model that wrote its answer in an earlier step and then calls `terminate` alone answers twice.
- Tests: a scripted run that calls `terminate` without text gets the "Not ended" result and ends with the next reply's text; mutations that end the run anyway or keep the original result fail it. The real Gemini behaviour is checked by `AgentRunExternalTest`.
