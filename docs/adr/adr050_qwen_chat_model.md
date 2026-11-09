# ADR 050: Qwen becomes a chat model, Gemini becomes optional

- **Status:** Accepted, 2026-10-24 (amends ADR 011; extends ADR 039 and 040); revised 2026-10-27 before merge after the pull request review (own settings, no starter)

## Context

Agents could run on DeepSeek or Gemini. On Gemini's free tier, a request as small as "Reply with OK" took 56–109 seconds before the first byte arrived (the connection itself took 0.02 s), so every Gemini agent run in the real-API tests hit the 120-second timeout. Waiting longer would hold an agent thread for minutes per step. A second fast provider was needed, without throwing away the Gemini work.

## Options considered

- **Raise the Gemini timeout** to several minutes: the runs might pass, but users would wait minutes per step, and a session's queued messages would wait behind them.
- **Qwen through Spring AI Alibaba**: Alibaba's own Spring AI integration; a third-party project whose versions must follow Spring AI 1.1.8.
- **Qwen through the DeepSeek client** pointed at Alibaba's endpoint: no new dependency, but a misleading class name and DeepSeek-specific handling.
- **Qwen through Spring AI's OpenAI client** pointed at Alibaba Cloud Model Studio's OpenAI-compatible endpoint.

## Decision

Add `qwen3.8-flash` (the newest Flash model on the key; it answered a tool-calling request in 2–3 seconds) as a third chat model, through Spring AI's OpenAI module, the documented way to reach an OpenAI-compatible provider. The module is used without its starter: the application builds the client itself with the same 10 s connect / 120 s read timeout and at most 3 attempts as DeepSeek (ADR 039), it never runs tools itself (ADR 040), and it reads only the project's own settings (`thinkxecai.chat.qwen.*`). Spring AI's shared `spring.ai.openai.*` names were tried first, but the review found that an OpenAI key or URL in a developer's environment (`SPRING_AI_OPENAI_API_KEY`, as Spring AI's own guide suggests) would silently replace Qwen's, sending that OpenAI key, or the conversations, to the wrong service. Without the starter, the module also adds no other model, so Ollama stays the only embedding model. Only the model is set as a default option, so Qwen uses its own temperature and thinking defaults. The endpoint is the international site (`dashscope-intl.aliyuncs.com`), the only one that accepts the project's key.

Gemini stays: its code, its tests that run without the network, and the option to save an agent with it. Its key becomes optional; without one the model counts as not configured, and a run on it leaves the usual "model is not available" notice instead of calling Google with an empty key. Gemini is no longer tested against the real API; the real-API tests run on DeepSeek and Qwen.

## Trade-offs

- The OpenAI module comes back as a dependency (it was removed when Gemini replaced OpenAI in ADR 011), used only as a client for Qwen. Its settings are not Spring AI's documented ones, so a second OpenAI-compatible provider would need its own settings in the same way.
- Gemini code without real-API tests can break unnoticed when Google's API changes; the offline tests cover only the application's own conversion and HTTP settings. Revisit with a paid Gemini key, or remove Gemini if it is not used.
- Qwen's thinking mode is left on (the model's default); its reasoning text is not shown or stored. Turning it off is a later tuning choice.
