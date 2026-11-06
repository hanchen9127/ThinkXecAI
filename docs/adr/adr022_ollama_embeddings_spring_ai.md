# ADR 022: Embeddings through Spring AI's Ollama integration

- **Status:** Accepted, 2026-10-11

## Context

Uploaded Markdown is split into sections, and each section title is turned into a vector by the `bge-m3` model running in a local Ollama. Searches embed the question the same way and return the closest sections by L2 distance. The starting design called Ollama's older `/api/embeddings` endpoint by hand, once per section. Measured on the same text, that endpoint returns vectors pointing the same way as the newer `/api/embed` (cosine 1.00000) but with lengths between 25.3 and 27.2 depending on the text, while `/api/embed` returns length 1.0. With L2 distance, unequal lengths shift the ranking, so the old search was not ranking by meaning alone.

## Options considered

- **Hand-written HTTP call to `/api/embeddings`** (starting design): one request per section, unnormalized vectors.
- **Hand-written HTTP client for `/api/embed`**: full control, but bypasses the AI framework the project already uses for chat.
- **Spring AI's Ollama module with a hand-written bean**: avoids an unwanted chat model, at the cost of wiring code with no clear gain.
- **Spring AI's Ollama starter**, used through the framework's `EmbeddingModel` interface.
- Ollama's OpenAI-compatible endpoint, an in-process ONNX model, or Spring AI's own vector store: each brings back a removed dependency, drops the required local Ollama setup, or imposes a table layout that does not fit the existing schema.

## Decision

Use the Spring AI Ollama starter. It calls `/api/embed`, which embeds a whole document's titles in one request and returns unit-length vectors, so L2 distance ranks exactly like cosine similarity. Business code depends only on `EmbeddingModel`, the same abstraction style as the chat models, so the provider can change through configuration. Two side effects were checked in the library code and pinned by tests: the starter would also add an Ollama chat model (its auto-configuration is excluded, because the shared on/off switch would also disable DeepSeek and Gemini), and the Gemini starter already registers its own embedding model under a different setting (turned off, so `bge-m3` is the only one). The embedding call has no retry, so an upload fails at once when Ollama is down.

## Trade-offs

- Relies on two setting names and one excluded class name from Spring AI 1.1.x; an upgrade could rename them. The configuration tests fail if that happens.
- Stored and query vectors must come from the same endpoint and model. Changing either means re-embedding all chunks.
- No read timeout is set for Ollama: a hung (not stopped) Ollama would hold an upload until the client gives up. Revisit if uploads move off localhost.
