package com.thinkxecai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins how bge-m3 embeddings are obtained, without a real Ollama: the base URL points at a fake
 * Ollama server that records each request. Ollama's normalized endpoint (/api/embed) must be used,
 * a batch must go out as one request, and a failure must not be retried (an upload then fails at once).
 */
@SpringBootTest
class EmbeddingModelConfigurationTest {

    private static final String FAILING_INPUT = "fail";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private static final HttpServer fakeOllama = startFakeOllama();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Map<String, EmbeddingModel> embeddingModels;

    @Autowired
    private EmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void pointOllamaAtFakeServer(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url",
                () -> "http://127.0.0.1:" + fakeOllama.getAddress().getPort());
    }

    @BeforeEach
    void clearRequests() {
        requests.clear();
    }

    @AfterAll
    static void stopFakeOllama() {
        fakeOllama.stop(0);
    }

    @Test
    void ollamaIsTheOnlyEmbeddingModelAndRegistersNoChatModel() {
        assertThat(embeddingModels.values())
                .hasSize(1)
                .hasExactlyElementsOfTypes(OllamaEmbeddingModel.class);
        assertThat(context.getBeanNamesForType(OllamaChatModel.class)).isEmpty();
    }

    @Test
    void embedsABatchWithBgeM3InOneRequestToTheNormalizedEndpoint() throws IOException {
        List<float[]> vectors = embeddingModel.embed(List.of("Installation", "FAQ"));

        assertThat(vectors).hasSize(2);
        assertThat(requests).hasSize(1);
        RecordedRequest request = requests.get(0);
        assertThat(request.path()).isEqualTo("/api/embed");
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.get("model").asText()).isEqualTo("bge-m3");
        assertThat(body.get("input")).extracting(JsonNode::asText).containsExactly("Installation", "FAQ");
    }

    @Test
    void aFailedEmbeddingCallIsNotRetried() {
        assertThatThrownBy(() -> embeddingModel.embed(FAILING_INPUT)).isInstanceOf(RuntimeException.class);
        assertThat(requests).hasSize(1);
    }

    private static HttpServer startFakeOllama() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", EmbeddingModelConfigurationTest::answer);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the fake Ollama server", e);
        }
    }

    /** Answers like Ollama's /api/embed: one 2-dimensional vector per input, or HTTP 500 for {@link #FAILING_INPUT}. */
    private static void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestURI().getPath(), body));

        JsonNode input = JSON.readTree(body).get("input");
        boolean fail = input.size() == 1 && FAILING_INPUT.equals(input.get(0).asText());
        String vectors = String.join(",", Collections.nCopies(input.size(), "[0.6,0.8]"));
        byte[] response = (fail ? "{\"error\":\"model failed\"}" : "{\"model\":\"bge-m3\",\"embeddings\":[" + vectors + "]}")
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(fail ? 500 : 200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private record RecordedRequest(String path, String body) {
    }

}
