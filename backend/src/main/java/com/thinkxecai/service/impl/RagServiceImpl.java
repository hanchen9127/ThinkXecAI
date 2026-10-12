package com.thinkxecai.service.impl;

import com.thinkxecai.mapper.ChunkMapper;
import com.thinkxecai.model.entity.ChunkEntity;
import com.thinkxecai.service.RagService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class RagServiceImpl implements RagService {

    private static final int TOP_K = 3;

    private final EmbeddingModel embeddingModel;
    private final ChunkMapper chunkMapper;
    private final TransactionTemplate readOnlyTransaction;

    public RagServiceImpl(EmbeddingModel embeddingModel, ChunkMapper chunkMapper,
                          PlatformTransactionManager transactionManager) {
        this.embeddingModel = embeddingModel;
        this.chunkMapper = chunkMapper;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.isEmpty() ? List.of() : embeddingModel.embed(texts);
    }

    /**
     * The query is embedded before the transaction starts, so no database connection is held while
     * Ollama works. The search itself needs a transaction for the scoped strict-order setting (ADR 024).
     */
    @Override
    public List<String> search(UUID kbId, String query) {
        float[] queryEmbedding = embeddingModel.embed(query);
        return readOnlyTransaction.execute(status -> {
            chunkMapper.enableStrictOrderScan();
            return chunkMapper.searchSimilar(kbId, queryEmbedding, TOP_K).stream()
                    .map(ChunkEntity::getContent)
                    .toList();
        });
    }

}
