-- ThinkXecAI core schema: agents, chat sessions/messages, knowledge bases, documents, vector chunks.

-- pgvector: provides the VECTOR column type and the <-> distance operator used by RAG similarity search.
CREATE EXTENSION IF NOT EXISTS vector;

-- A configurable AI agent: its persona, which chat model backs it, and which tools/knowledge bases it may use.
CREATE TABLE agent (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT NOT NULL,
    description    TEXT,
    system_prompt  TEXT,                                                          -- prompt applied at runtime; never persisted as a chat_message row
    model          TEXT NOT NULL,                                                 -- must match a registered ChatClient bean name, e.g. 'deepseek-chat' or 'gemini-3.8-flash'
    allowed_tools  JSONB NOT NULL DEFAULT '[]'::jsonb,                            -- array of optional tool names selected for this agent (fixed tools are always available and are not listed here)
    allowed_kbs    JSONB NOT NULL DEFAULT '[]'::jsonb,                            -- array of knowledge_base.id values this agent may query; resolved via batch lookup, not a join table
    chat_options   JSONB NOT NULL DEFAULT '{"temperature": 0.7, "topP": 1.0, "messageLength": 10}'::jsonb, -- temperature/topP are stored only (not applied to runtime ChatOptions); messageLength caps how many recent messages are loaded into memory
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- One conversation thread between a user and an agent.
CREATE TABLE chat_session (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id    UUID REFERENCES agent(id) ON DELETE SET NULL,                     -- SET NULL (not CASCADE): deleting an agent keeps its past chat history around
    title       TEXT,                                                             -- display label for the session, e.g. in a sidebar list
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- A single message within a chat session: from the user, the assistant, the system prompt, or a tool result.
CREATE TABLE chat_message (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,      -- deleting a session deletes its messages
    role        TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system', 'tool')),
    content     TEXT,
    metadata    JSONB,                                                            -- written only by the agent runtime: {"toolCalls": [{id, type, name, arguments}]} on assistant messages, {"toolResponse": {id, name, responseData}} on tool messages, else NULL
    created_at  TIMESTAMP NOT NULL DEFAULT clock_timestamp(),                     -- clock_timestamp(), not now(): now() is frozen per transaction, so messages saved together would tie and ORDER BY created_at would lose their order
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- A collection of documents that can be searched by an agent via RAG retrieval.
CREATE TABLE knowledge_base (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- An uploaded source file (currently Markdown) belonging to one knowledge base.
CREATE TABLE document (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id       UUID NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,    -- deleting a knowledge base deletes its documents
    name        TEXT NOT NULL,
    metadata    JSONB,                                                            -- holds the on-disk relative file path plus any other file details; there is no dedicated file_path column
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- One embedded chunk of a document (one row per parsed Markdown section), used for pgvector similarity search.
CREATE TABLE chunk_bge_m3 (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id       UUID NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,    -- denormalized from document.kb_id so similarity search can filter by kb without a join
    doc_id      UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,          -- deleting a document deletes its chunks
    title       TEXT,                                                             -- section heading; embedded via bge-m3 to produce `embedding`
    content     TEXT,                                                             -- section body text returned to the agent on retrieval
    embedding   VECTOR(1024),                                                     -- bge-m3 embedding of `title`, fixed at 1024 dimensions
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Approximate nearest-neighbor index so `ORDER BY embedding <-> :query LIMIT 3` stays fast as chunks grow.
-- HNSW, not ivfflat: ivfflat trains its list centroids on the rows present at build time, and this
-- script runs on an empty table, so small knowledge bases could get fewer than 3 (or zero) results.
-- HNSW builds incrementally and needs no training data. vector_l2_ops matches the <-> operator.
-- HNSW alone still filters by kb_id after collecting ~40 neighbours from all knowledge bases, so the
-- search also sets hnsw.iterative_scan = strict_order for its transaction (ADR 024).
CREATE INDEX chunk_bge_m3_embedding_idx ON chunk_bge_m3
    USING hnsw (embedding vector_l2_ops);

-- Supporting indexes for the foreign keys above, since Postgres does not create them automatically.
CREATE INDEX chat_session_agent_id_idx ON chat_session(agent_id);
CREATE INDEX chat_message_session_id_idx ON chat_message(session_id);
CREATE INDEX document_kb_id_idx ON document(kb_id);
CREATE INDEX chunk_bge_m3_kb_id_idx ON chunk_bge_m3(kb_id);
CREATE INDEX chunk_bge_m3_doc_id_idx ON chunk_bge_m3(doc_id);
