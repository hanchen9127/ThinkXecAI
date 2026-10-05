package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.ChunkEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkMapperTest extends MapperTestSupport {

    private static final int DIMENSIONS = 1024;

    @Autowired
    private ChunkMapper mapper;

    @Test
    void insertReadsBackIdAndTheExactEmbedding() {
        UUID kb = insertKnowledgeBase("kb");
        float[] embedding = new float[DIMENSIONS];
        Random random = new Random(42);
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] = random.nextFloat() * 2 - 1;
        }

        ChunkEntity chunk = insert(kb, insertDocument(kb, "doc.md"), "Refunds", "Refunds take 5 days.", embedding);

        ChunkEntity found = mapper.selectById(chunk.getId());
        assertThat(chunk.getId()).isNotNull();
        assertThat(chunk.getCreatedAt()).isNotNull();
        assertThat(found.getEmbedding()).containsExactly(embedding);
        assertThat(found.getContent()).isEqualTo("Refunds take 5 days.");
    }

    @Test
    void searchSimilarReturnsTheNearestChunksNearestFirstAndAtMostLimit() {
        UUID kb = insertKnowledgeBase("kb");
        UUID doc = insertDocument(kb, "doc.md");
        for (int i = 0; i < 5; i++) {
            insert(kb, doc, "e" + i, "content " + i, unit(i));
        }
        // L2 distances to the query: e2 = 0.54, e3 = 1.14, e4 = 1.37, e0 = e1 = 1.51.
        float[] query = vector(2, 1.0f, 3, 0.5f, 4, 0.2f);

        List<ChunkEntity> results = mapper.searchSimilar(kb, query, 3);

        assertThat(results).extracting(ChunkEntity::getTitle).containsExactly("e2", "e3", "e4");
        assertThat(results).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());
    }

    @Test
    void searchSimilarNeverReturnsChunksFromAnotherKnowledgeBase() {
        UUID kb = insertKnowledgeBase("kb");
        UUID otherKb = insertKnowledgeBase("other");
        insert(kb, insertDocument(kb, "mine.md"), "mine", "mine", unit(0));
        // An exact match for the query, but in a different knowledge base.
        insert(otherKb, insertDocument(otherKb, "theirs.md"), "theirs", "theirs", unit(7));

        List<ChunkEntity> results = mapper.searchSimilar(kb, unit(7), 3);

        assertThat(results).extracting(ChunkEntity::getTitle).containsExactly("mine");
    }

    @Test
    void searchSimilarOnAnEmptyKnowledgeBaseReturnsNothing() {
        assertThat(mapper.searchSimilar(insertKnowledgeBase("empty"), unit(0), 3)).isEmpty();
    }

    @Test
    void listAndDeleteByDocIdOnlyTouchThatDocument() {
        UUID kb = insertKnowledgeBase("kb");
        UUID doc = insertDocument(kb, "a.md");
        UUID otherDoc = insertDocument(kb, "b.md");
        insert(kb, doc, "a1", "a1", unit(1));
        insert(kb, doc, "a2", "a2", unit(2));
        insert(kb, otherDoc, "b1", "b1", unit(3));

        assertThat(mapper.listByDocId(doc)).extracting(ChunkEntity::getTitle).containsExactlyInAnyOrder("a1", "a2");
        assertThat(mapper.deleteByDocId(doc)).isEqualTo(2);
        assertThat(mapper.listByDocId(doc)).isEmpty();
        assertThat(mapper.listByDocId(otherDoc)).extracting(ChunkEntity::getTitle).containsExactly("b1");
    }

    @Test
    void deletingADocumentDeletesItsChunks() {
        UUID kb = insertKnowledgeBase("kb");
        UUID doc = insertDocument(kb, "a.md");
        insert(kb, doc, "a1", "a1", unit(1));

        jdbcTemplate.update("DELETE FROM document WHERE id = ?", doc);

        assertThat(mapper.listByDocId(doc)).isEmpty();
    }

    @Test
    void embeddingWithWrongDimensionsIsRejected() {
        // Last statement on purpose: a failed statement aborts the rest of the test transaction.
        UUID kb = insertKnowledgeBase("kb");
        UUID doc = insertDocument(kb, "doc.md");

        assertThatThrownBy(() -> insert(kb, doc, "short", "short", new float[]{0.1f, 0.2f, 0.3f}))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("1024");
    }

    private ChunkEntity insert(UUID kbId, UUID docId, String title, String content, float[] embedding) {
        ChunkEntity chunk = ChunkEntity.builder()
                .kbId(kbId).docId(docId).title(title).content(content).embedding(embedding).build();
        mapper.insert(chunk);
        return chunk;
    }

    private static float[] unit(int dimension) {
        return vector(dimension, 1.0f);
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
