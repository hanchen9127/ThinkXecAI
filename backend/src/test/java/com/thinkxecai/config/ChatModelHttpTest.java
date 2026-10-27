package com.thinkxecai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives every chat model bean against a local fake server, so the application's own HTTP clients are
 * what is tested (ADR 039): a model that does not answer fails after the read timeout (1 s here)
 * instead of waiting, a server error is tried exactly 3 times, a client error once. DeepSeek and Qwen
 * (OpenAI-compatible, ADR 050) share those cases; Qwen's requests carry its key and model. The Gemini cases
 * also run a text + function-call response through the real bean and send it back (ADR 038). The
 * registry's clients reach their own model and hand tool calls back without running them (ADR 040).
 */
@SpringBootTest
class ChatModelHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GEMINI_KEY = "test-gemini-key";
    private static final String QWEN_KEY = "test-qwen-key";
    private static final String OPENAI_KEY = "test-openai-key";
    private static final String QWEN_PATH = "/compatible-mode/v1/chat/completions";
    private static final byte[] SIGNATURE = "signature-1".getBytes(StandardCharsets.UTF_8);
    private static final Duration SLOW_ANSWER = Duration.ofSeconds(5);
    private static final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private static final HttpServer fakeServer = startFakeServer();

    /** How the fake server answers: a normal reply, too late, or an HTTP error. */
    private enum Mode { OK, SLOW, SERVER_ERROR, CLIENT_ERROR }

    private static volatile Mode mode = Mode.OK;

    @Autowired
    private ThinkXecGoogleGenAiChatModel geminiChatModel;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    @Autowired
    private OpenAiChatModel qwenChatModel;

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @DynamicPropertySource
    static void pointModelsAtFakeServer(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + fakeServer.getAddress().getPort();
        registry.add("thinkxecai.chat.gemini-base-url", () -> url);
        registry.add("spring.ai.google.genai.api-key", () -> GEMINI_KEY);
        registry.add("spring.ai.deepseek.base-url", () -> url);
        registry.add("thinkxecai.chat.qwen.base-url", () -> url + "/compatible-mode");
        registry.add("thinkxecai.chat.qwen.api-key", () -> QWEN_KEY);
        // As SPRING_AI_OPENAI_* in a developer's environment would set them: Qwen must ignore these (ADR 050).
        registry.add("spring.ai.openai.base-url", () -> url + "/openai");
        registry.add("spring.ai.openai.api-key", () -> OPENAI_KEY);
        registry.add("spring.ai.openai.chat.base-url", () -> url + "/openai-chat");
        registry.add("spring.ai.openai.chat.api-key", () -> OPENAI_KEY);
        registry.add("spring.ai.openai.chat.options.model", () -> "gpt-5.4-mini");
        registry.add("thinkxecai.chat.read-timeout", () -> "1s");
        registry.add("spring.ai.retry.backoff.initial-interval", () -> "10ms");
        registry.add("spring.ai.retry.backoff.max-interval", () -> "50ms");
    }

    @BeforeEach
    void reset() {
        requests.clear();
        mode = Mode.OK;
    }

    @AfterAll
    static void stopFakeServer() {
        fakeServer.stop(0);
    }

    /** Spring AI would otherwise run the returned call itself (no such tool is registered here). */
    private static Prompt geminiPrompt(List<Message> messages) {
        return new Prompt(messages, GoogleGenAiChatOptions.builder().internalToolExecutionEnabled(false).build());
    }

    @Test
    void geminiTextAndACallComeBackAsOneMessageAndTheSignatureIsSentBackOnTheCall() throws IOException {
        UserMessage question = new UserMessage("What is the date?");

        ChatResponse response = geminiChatModel.call(geminiPrompt(List.of(question)));

        assertThat(response.getResults()).hasSize(1);
        AssistantMessage answer = response.getResult().getOutput();
        assertThat(answer.getText()).isEqualTo("Let me check.");
        assertThat(answer.getToolCalls()).extracting(AssistantMessage.ToolCall::name).containsExactly("getDate");

        RecordedRequest first = requests.get(0);
        assertThat(first.path()).isEqualTo("/v1beta/models/gemini-3.8-flash:generateContent");
        assertThat(first.apiKey()).isEqualTo(GEMINI_KEY);

        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("", "getDate", "{\"date\":\"2026-10-18\"}")))
                .build();
        geminiChatModel.call(geminiPrompt(List.of(question, answer, toolResult)));

        JsonNode modelTurn = JSON.readTree(requests.get(1).body()).get("contents").get(1);
        assertThat(modelTurn.get("role").asText()).isEqualTo("model");
        JsonNode parts = modelTurn.get("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).get("text").asText()).isEqualTo("Let me check.");
        assertThat(parts.get(0).has("thoughtSignature")).isFalse();
        assertThat(parts.get(1).get("functionCall").get("name").asText()).isEqualTo("getDate");
        assertThat(parts.get(1).get("thoughtSignature").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(SIGNATURE));
    }

    @Test
    void geminiThatDoesNotAnswerFailsAfterTheTimeout() {
        mode = Mode.SLOW;
        Instant start = Instant.now();

        assertThatThrownBy(() -> geminiChatModel.call(geminiPrompt(List.of(new UserMessage("hi")))))
                .isInstanceOf(RuntimeException.class);

        // The SDK's timeout covers the whole call, retries included; the server would answer after 5 s.
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(3_000));
    }

    @Test
    void geminiServerErrorsAreTriedThreeTimes() {
        mode = Mode.SERVER_ERROR;

        assertThatThrownBy(() -> geminiChatModel.call(geminiPrompt(List.of(new UserMessage("hi")))))
                .isInstanceOf(RuntimeException.class);

        assertThat(requests).hasSize(3);
    }

    @Test
    void geminiClientErrorsAreNotRetried() {
        mode = Mode.CLIENT_ERROR;

        assertThatThrownBy(() -> geminiChatModel.call(geminiPrompt(List.of(new UserMessage("hi")))))
                .isInstanceOf(RuntimeException.class);

        assertThat(requests).hasSize(1);
    }

    @Test
    void deepSeekAnswersThroughItsConfiguredClient() {
        ChatResponse response = deepSeekChatModel.call(new Prompt("hi"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello.");
        assertThat(requests).singleElement().extracting(RecordedRequest::path).isEqualTo("/chat/completions");
    }

    @Test
    void qwenUsesItsOwnEndpointKeyAndModelWhateverTheOpenAiSettingsSay() throws IOException {
        ChatResponse response = qwenChatModel.call(new Prompt("hi"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello.");
        assertThat(requests).hasSize(1);
        RecordedRequest request = requests.get(0);
        assertThat(request.path()).isEqualTo(QWEN_PATH);
        assertThat(request.authorization()).isEqualTo("Bearer " + QWEN_KEY);
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.get("model").asText()).isEqualTo("qwen3.8-flash");
        assertThat(body.has("temperature")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void anOpenAiCompatibleModelThatDoesNotAnswerTimesOutOnEveryAttempt(String model) {
        mode = Mode.SLOW;

        assertThatThrownBy(() -> openAiCompatible(model).call(new Prompt("hi"))).isInstanceOf(RuntimeException.class);

        // Each attempt gives up after 1 s instead of taking the answer that arrives after 5 s.
        assertThat(requests).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void anOpenAiCompatibleModelTriesServerErrorsThreeTimes(String model) {
        mode = Mode.SERVER_ERROR;

        assertThatThrownBy(() -> openAiCompatible(model).call(new Prompt("hi"))).isInstanceOf(RuntimeException.class);

        assertThat(requests).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void anOpenAiCompatibleModelDoesNotRetryClientErrors(String model) {
        mode = Mode.CLIENT_ERROR;

        assertThatThrownBy(() -> openAiCompatible(model).call(new Prompt("hi"))).isInstanceOf(RuntimeException.class);

        assertThat(requests).hasSize(1);
    }

    private ChatModel openAiCompatible(String model) {
        return "deepseek-chat".equals(model) ? deepSeekChatModel : qwenChatModel;
    }

    @Test
    void eachRegisteredClientCallsItsOwnModel() throws IOException {
        String deepSeekAnswer = chatClientRegistry.get("deepseek-chat").prompt("hi").call().content();
        chatClientRegistry.get("gemini-3.8-flash").prompt("hi").call().content();
        String qwenAnswer = chatClientRegistry.get("qwen3.8-flash").prompt("hi").call().content();

        assertThat(deepSeekAnswer).isEqualTo("Hello.");
        assertThat(qwenAnswer).isEqualTo("Hello.");
        assertThat(requests).extracting(RecordedRequest::path)
                .containsExactly("/chat/completions", "/v1beta/models/gemini-3.8-flash:generateContent", QWEN_PATH);
        assertThat(JSON.readTree(requests.get(0).body()).get("model").asText()).isEqualTo("deepseek-chat");
        assertThat(JSON.readTree(requests.get(2).body()).get("model").asText()).isEqualTo("qwen3.8-flash");
    }

    @Test
    void registeredClientsHandToolCallsBackWithoutRunningTheTool() {
        AtomicInteger toolRuns = new AtomicInteger();
        ToolCallback getDate = recordingTool(toolRuns);

        for (String model : List.of("deepseek-chat", "gemini-3.8-flash", "qwen3.8-flash")) {
            ChatResponse response = chatClientRegistry.get(model).prompt("What is the date?")
                    .toolCallbacks(getDate)
                    .call()
                    .chatResponse();

            assertThat(response.getResult().getOutput().getToolCalls())
                    .extracting(AssistantMessage.ToolCall::name)
                    .as(model)
                    .containsExactly("getDate");
        }
        assertThat(toolRuns).hasValue(0);
        assertThat(requests).hasSize(3);
    }

    private static ToolCallback recordingTool(AtomicInteger runs) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("getDate")
                        .description("Returns today's date.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                runs.incrementAndGet();
                return "2026-10-19";
            }
        };
    }

    private static HttpServer startFakeServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ChatModelHttpTest::answer);
            // Slow answers sleep in their handler; other requests (retries) must not wait behind them.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the fake chat model server", e);
        }
    }

    /** Answers like Gemini's generateContent or an OpenAI-style chat completion (DeepSeek, Qwen), by path. */
    private static void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(path, exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                exchange.getRequestHeaders().getFirst("Authorization"), body));

        boolean gemini = path.startsWith("/v1beta/");
        switch (mode) {
            case SLOW -> {
                sleep(SLOW_ANSWER);
                respond(exchange, 200, gemini ? geminiAnswer() : deepSeekAnswer());
            }
            case SERVER_ERROR -> respond(exchange, 503, "{\"error\":{\"code\":503,\"message\":\"overloaded\"}}");
            case CLIENT_ERROR -> respond(exchange, 400, "{\"error\":{\"code\":400,\"message\":\"bad request\"}}");
            case OK -> respond(exchange, 200, gemini ? geminiAnswer()
                    : body.contains("\"tools\"") ? deepSeekToolCallAnswer() : deepSeekAnswer());
        }
    }

    /** A Gemini 3 style candidate: a sentence, then a signed function call. */
    private static String geminiAnswer() {
        return """
                {"candidates":[{"index":0,"finishReason":"STOP","content":{"role":"model","parts":[
                  {"text":"Let me check."},
                  {"functionCall":{"name":"getDate","args":{}},"thoughtSignature":"%s"}]}}],
                 "modelVersion":"gemini-3.8-flash",
                 "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":3,"totalTokenCount":8}}
                """.formatted(Base64.getEncoder().encodeToString(SIGNATURE));
    }

    private static String deepSeekAnswer() {
        return """
                {"id":"chat-1","object":"chat.completion","created":1760745600,"model":"deepseek-chat",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"Hello."},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
                """;
    }

    /** The OpenAI-style answer (DeepSeek, Qwen) when the request offers tools: a call to {@code getDate}. */
    private static String deepSeekToolCallAnswer() {
        return """
                {"id":"chat-2","object":"chat.completion","created":1760745600,"model":"deepseek-chat",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":"",
                   "tool_calls":[{"id":"call_1","type":"function","function":{"name":"getDate","arguments":"{}"}}]}}],
                 "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
                """;
    }

    private static void respond(HttpExchange exchange, int status, String json) {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = exchange.getResponseBody()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            out.write(response);
        } catch (IOException e) {
            // The client has given up (timeout); nothing to answer.
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RecordedRequest(String path, String apiKey, String authorization, String body) {
    }

}
