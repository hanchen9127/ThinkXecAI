package com.thinkxecai.service;

import com.thinkxecai.mapper.ChunkMapper;
import com.thinkxecai.model.entity.ChunkEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest extends ServiceTestSupport {

    private static final int DIMENSIONS = 1024;
    private static final String QUERY = "query";

    @Autowired
    private RagService ragService;

    @MockitoSpyBean
    private ChunkMapper chunkMapper;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Test
    void aSmallKnowledgeBaseGetsAllItsNearestChunksThroughTheFilteredIndexScan() {
        UUID smallKb = insertCrowdedNeighbourhood();
        forceTheHnswIndexScan();

        assertThat(ragService.search(smallKb, QUERY)).containsExactly("far 1", "far 2", "far 3");
    }

    /** Proves the setup above: the same index scan without strict ordering finds none of the 3 chunks. */
    @Test
    void withoutStrictOrderTheSameIndexScanReturnsNothingForTheSmallKnowledgeBase() {
        UUID smallKb = insertCrowdedNeighbourhood();
        forceTheHnswIndexScan();

        assertThat(chunkMapper.searchSimilar(smallKb, queryVector(), 3)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void theQueryIsEmbeddedBeforeAnyTransactionStarts() {
        AtomicBoolean transactionDuringEmbedding = new AtomicBoolean(true);
        when(embeddingModel.embed(QUERY)).thenAnswer(invocation -> {
            transactionDuringEmbedding.set(TransactionSynchronizationManager.isActualTransactionActive());
            return queryVector();
        });

        assertThat(ragService.search(UUID.randomUUID(), QUERY)).isEmpty();
        assertThat(transactionDuringEmbedding).isFalse();
    }

    /**
     * Without the test's own transaction: the search must open a read-only one, or its SET LOCAL is
     * lost (outside a transaction PostgreSQL only warns) and the strict-order scan silently does not apply.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void theSearchRunsInItsOwnReadOnlyTransactionWithStrictOrdering() {
        when(embeddingModel.embed(QUERY)).thenReturn(queryVector());
        List<String> duringSearch = new ArrayList<>();
        doAnswer(invocation -> {
            duringSearch.add("read-only " + TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            duringSearch.add(jdbcTemplate.queryForObject("SELECT current_setting('hnsw.iterative_scan', true)", String.class));
            return List.of();
        }).when(chunkMapper).searchSimilar(any(), any(), anyInt());

        ragService.search(UUID.randomUUID(), QUERY);

        assertThat(duringSearch).containsExactly("read-only true", "strict_order");
    }

    @Test
    void searchReturnsTheThreeNearestOfMoreChunks() {
        when(embeddingModel.embed(QUERY)).thenReturn(queryVector());
        UUID kb = insertKnowledgeBase("kb");
        UUID doc = insertDocument(kb, "doc.md");
        for (int i = 5; i >= 1; i--) {
            insertChunk(kb, doc, "chunk " + i, vector(0, 1f, 3, i / 10f));
        }

        assertThat(ragService.search(kb, QUERY)).containsExactly("chunk 1", "chunk 2", "chunk 3");
    }

    @Test
    void embedReturnsTheModelsVectorsFromOneCall() {
        List<float[]> vectors = List.of(new float[]{1f}, new float[]{2f});
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(vectors);

        assertThat(ragService.embed(List.of("a", "b"))).isSameAs(vectors);
        verify(embeddingModel).embed(List.of("a", "b"));
    }

    @Test
    void embeddingNoTextsDoesNotCallTheModel() {
        assertThat(ragService.embed(List.of())).isEmpty();
        verify(embeddingModel, never()).embed(anyList());
    }

    /**
     * 200 chunks right next to the query in one knowledge base, and 3 farther away in another (the
     * Phase 6 measurement). Returns the small knowledge base; its chunks are "far 1" < "far 2" < "far 3"
     * by distance to the query.
     */
    private UUID insertCrowdedNeighbourhood() {
        when(embeddingModel.embed(QUERY)).thenReturn(queryVector());
        UUID bigKb = insertKnowledgeBase("big");
        UUID bigDoc = insertDocument(bigKb, "big.md");
        for (int i = 1; i <= 200; i++) {
            insertChunk(bigKb, bigDoc, "near " + i, vector(0, 1f, 1, i / 1000f));
        }
        UUID smallKb = insertKnowledgeBase("small");
        UUID smallDoc = insertDocument(smallKb, "small.md");
        for (int i = 1; i <= 3; i++) {
            insertChunk(smallKb, smallDoc, "far " + i, vector(1, 1f, 2, i / 1000f));
        }
        return smallKb;
    }

    /**
     * With sorting disabled the only way to return rows by distance is the HNSW index. Disabling only
     * sequential scans is not enough: the planner then uses the kb_id index plus an exact sort.
     */
    private void forceTheHnswIndexScan() {
        jdbcTemplate.execute("SET LOCAL enable_sort = off");
    }

    private void insertChunk(UUID kbId, UUID docId, String title, float[] embedding) {
        chunkMapper.insert(ChunkEntity.builder()
                .kbId(kbId).docId(docId).title(title).content(title).embedding(embedding).build());
    }

    private static float[] queryVector() {
        return vector(0, 1f);
    }

    /** A 1024-dimension vector from (dimension, value) pairs; all other entries are 0. */
    private static float[] vector(Object... dimensionValuePairs) {
        float[] v = new float[DIMENSIONS];
        for (int i = 0; i < dimensionValuePairs.length; i += 2) {
            v[(int) dimensionValuePairs[i]] = (float) dimensionValuePairs[i + 1];
        }
        return v;
    }

}
