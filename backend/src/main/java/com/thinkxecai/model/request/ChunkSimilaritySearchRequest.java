package com.thinkxecai.model.request;

import lombok.Data;

/** Used internally by RAG retrieval (the knowledge tool): embed queryText and search within kbId. */
@Data
public class ChunkSimilaritySearchRequest {

    private String kbId;
    private String queryText;

}
