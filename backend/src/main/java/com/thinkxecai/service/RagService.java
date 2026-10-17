package com.thinkxecai.service;

import java.util.List;
import java.util.UUID;

/** Embeddings from local Ollama bge-m3 and similarity search over a knowledge base's chunks. */
public interface RagService {

    /** One unit-length 1024-dimension vector per text, in order, from a single embedding request. */
    List<float[]> embed(List<String> texts);

    /**
     * Contents of the 3 chunks of the knowledge base whose titles are nearest to the query, nearest
     * first; empty for an unknown knowledge base. Fewer when the knowledge base has fewer chunks, or,
     * on a very large table, when the index scan stops at {@code hnsw.max_scan_tuples} first (ADR 024).
     */
    List<String> search(UUID kbId, String query);

}
