package com.thinkxecai.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Calls the local Ollama bge-m3 model (needs Ollama running with bge-m3 pulled). Vectors must fit
 * {@code chunk_bge_m3.embedding VECTOR(1024)} and have unit length, so L2 distance ranks like cosine similarity.
 */
@Tag("external")
@SpringBootTest
class OllamaEmbeddingExternalTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void bgeM3ReturnsUnitLength1024DimensionVectors() {
        List<float[]> vectors = embeddingModel.embed(List.of("安装", "How to configure the HNSW index"));

        assertThat(vectors).hasSize(2).allSatisfy(vector -> {
            assertThat(vector).hasSize(1024);
            assertThat(length(vector)).isCloseTo(1.0, within(1e-3));
        });
    }

    private static double length(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

}
