package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.ChunkEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * SQL lives in resources/mapper/ChunkMapper.xml. Embeddings travel as {@code float[]} and are
 * converted to the pgvector text literal by PgVectorTypeHandler, so the XML casts them with
 * {@code ::vector}.
 */
public interface ChunkMapper {

    /** Fills in the database-generated id and createdAt. {@code embedding} must have 1024 dimensions. */
    int insert(ChunkEntity entity);

    /** Includes the embedding. */
    ChunkEntity selectById(@Param("id") UUID id);

    /** Chunks of one document, without embeddings. */
    List<ChunkEntity> listByDocId(@Param("docId") UUID docId);

    /** Removes a document's chunks before it is re-ingested; returns the number deleted. */
    int deleteByDocId(@Param("docId") UUID docId);

    /**
     * The {@code limit} chunks of one knowledge base closest to {@code queryEmbedding} by L2
     * distance, nearest first. Returned without embeddings: retrieval only needs the text.
     */
    List<ChunkEntity> searchSimilar(@Param("kbId") UUID kbId,
                                    @Param("queryEmbedding") float[] queryEmbedding,
                                    @Param("limit") int limit);

}
