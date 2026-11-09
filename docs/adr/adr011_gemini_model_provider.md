# ADR 011: Gemini replaces OpenAI as the second chat model

- **Status:** Accepted, 2026-10-06

## Context

Agents can run on one of two chat models. The second one was OpenAI `gpt-5.4-mini`; the project switches it to Google Gemini and keeps DeepSeek. The agent depends on tool calling (the model asks the application to run a tool, then continues with the result), so the provider has to support it reliably through the project's AI framework, Spring AI.

## Options considered

- **Keep OpenAI.**
- **Gemini through its OpenAI-compatible endpoint**, keeping the OpenAI client: least code change, but Spring AI 1.x drops a Gemini-specific field that Gemini 3 requires during tool calling, which causes request errors.
- **Gemini through Vertex AI**: requires a Google Cloud project and service credentials instead of an API key.
- **Gemini Developer API through Spring AI's native Google GenAI integration**, with an API key.

## Decision

Use the native Google GenAI integration with an API key and the model `gemini-3.8-flash`, the newest stable Flash model available to the project's key. Spring AI was raised from 1.1.0 to 1.1.8 first, in a separate commit: Gemini 3 sends "thought signatures" that must be returned during tool calling, and Spring AI handles them only from 1.1.1. The model name is set explicitly because Spring AI's built-in default (`gemini-2.0-flash`) has been shut down; a test pins it, and removing the setting makes that test fail.

## Trade-offs

- Spring AI 1.x has a known bug: when Gemini returns text and a tool call in the same response, the tool call is dropped (fixed only in Spring AI 2). A small subclass workaround is planned for the chat model registry phase, to be removed once a release contains the fix.
- Gemini returns tool calls without ids, while DeepSeek requires them. The agent runtime will assign ids when saving, so a conversation can switch between the two models.
- Thought signatures are kept within one agent run but not stored, so earlier turns are sent without them; Gemini only checks the current turn.
- No real model call runs in CI (placeholder keys); real calls are covered by separately tagged tests.
