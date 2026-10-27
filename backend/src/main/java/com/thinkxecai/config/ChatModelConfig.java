package com.thinkxecai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import com.thinkxecai.model.ChatModelNames;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds every chat model's HTTP client, replacing Spring AI's auto-configured beans (which back off
 * when these exist), so that a model call cannot hang or retry for minutes while it holds an agent
 * thread (ADR 039). Without this, no client had a timeout, DeepSeek retried 5xx and network
 * errors 10 times up to 3 minutes apart, and the Gemini SDK retried 5 times up to 60 seconds apart.
 * <p>
 * DeepSeek and Qwen: a JDK request factory with {@code thinkxecai.chat.connect-timeout} and
 * {@code thinkxecai.chat.read-timeout} per attempt; retries come from Spring AI's {@code RetryTemplate}
 * ({@code spring.ai.retry.*}). Qwen is called through its OpenAI-compatible endpoint with Spring AI's
 * OpenAI client (ADR 050). Gemini: the google-genai SDK only offers a timeout for the whole call,
 * which cancels the request but not the SDK's waits between attempts: after the cancel each remaining
 * attempt fails at once, but the SDK still waits before it, so a hanging Gemini call ends after the
 * read timeout plus all remaining waits (2 s + 6 s with 3 attempts). Spring AI's Gemini model wraps
 * every SDK error so its {@code RetryTemplate} never retries; the SDK's own retry gets the same
 * {@code spring.ai.retry.*} values instead, without the SDK's random jitter, so all models wait the
 * same intervals.
 * <p>
 * A timeout of zero would mean "no timeout" to the Gemini SDK and "fail at once" to the JDK client,
 * so both settings must be positive; startup fails otherwise.
 * <p>
 * The {@link ChatClient} beans are named after {@link ChatModelNames}; {@code ChatClientRegistry}
 * looks them up by an agent's {@code model}.
 */
@Configuration
public class ChatModelConfig {

    private static final String CONNECT_TIMEOUT = "thinkxecai.chat.connect-timeout";
    private static final String READ_TIMEOUT = "thinkxecai.chat.read-timeout";

    @Bean
    public Client googleGenAiClient(GoogleGenAiConnectionProperties connectionProperties,
                                    SpringAiRetryProperties retryProperties,
                                    @Value("${thinkxecai.chat.gemini-base-url}") String baseUrl,
                                    @Value("${thinkxecai.chat.read-timeout}") Duration readTimeout) {
        requirePositive(READ_TIMEOUT, readTimeout);
        SpringAiRetryProperties.Backoff backoff = retryProperties.getBackoff();
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
                .attempts(retryProperties.getMaxAttempts())
                .initialDelay(seconds(backoff.getInitialInterval()))
                .expBase((double) backoff.getMultiplier())
                .maxDelay(seconds(backoff.getMaxInterval()))
                .jitter(0.0)
                .build();
        return Client.builder()
                .apiKey(connectionProperties.getApiKey())
                .httpOptions(HttpOptions.builder()
                        .baseUrl(baseUrl)
                        .timeout(Math.toIntExact(readTimeout.toMillis()))
                        .retryOptions(retryOptions)
                        .build())
                .build();
    }

    @Bean
    public ThinkXecGoogleGenAiChatModel googleGenAiChatModel(
            Client googleGenAiClient, GoogleGenAiChatProperties chatProperties, ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate, ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        ThinkXecGoogleGenAiChatModel chatModel = new ThinkXecGoogleGenAiChatModel(googleGenAiClient,
                chatProperties.getOptions(), toolCallingManager, retryTemplate,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new));
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }

    @Bean
    public DeepSeekChatModel deepSeekChatModel(
            DeepSeekConnectionProperties connectionProperties, DeepSeekChatProperties chatProperties,
            ObjectProvider<RestClient.Builder> restClientBuilder, ResponseErrorHandler responseErrorHandler,
            ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
            @Value("${thinkxecai.chat.connect-timeout}") Duration connectTimeout,
            @Value("${thinkxecai.chat.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(connectTimeout, readTimeout);
        // Same resolution as the auto-configuration: a chat-level setting wins over the shared one.
        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .baseUrl(firstSet(chatProperties.getBaseUrl(), connectionProperties.getBaseUrl()))
                .apiKey(firstSet(chatProperties.getApiKey(), connectionProperties.getApiKey()))
                .completionsPath(chatProperties.getCompletionsPath())
                .betaPrefixPath(chatProperties.getBetaPrefixPath())
                .restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder).requestFactory(requestFactory))
                .responseErrorHandler(responseErrorHandler)
                .build();
        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(chatProperties.getOptions())
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(
                        toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }

    /**
     * Qwen's endpoint, from {@code thinkxecai.chat.qwen.*} only: Spring AI's shared {@code spring.ai.openai.*}
     * names would let an OpenAI key or URL in the environment ({@code SPRING_AI_OPENAI_*}) replace them, and
     * send that key, or the conversation, to the wrong service (ADR 050).
     */
    @Bean
    public OpenAiApi qwenOpenAiApi(
            @Value("${thinkxecai.chat.qwen.base-url}") String baseUrl,
            @Value("${thinkxecai.chat.qwen.api-key}") String apiKey,
            ObjectProvider<RestClient.Builder> restClientBuilder, ResponseErrorHandler responseErrorHandler,
            @Value("${thinkxecai.chat.connect-timeout}") Duration connectTimeout,
            @Value("${thinkxecai.chat.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(connectTimeout, readTimeout);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder).requestFactory(requestFactory))
                .responseErrorHandler(responseErrorHandler)
                .build();
    }

    /** Only the model is set: no temperature or topP is sent, so Qwen uses its own defaults. */
    @Bean
    public OpenAiChatModel qwenChatModel(
            OpenAiApi qwenOpenAiApi, @Value("${thinkxecai.chat.qwen.model}") String model,
            ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(qwenOpenAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(
                        toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }

    @Bean(ChatModelNames.DEEPSEEK_CHAT)
    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekChatModel) {
        return chatClient(deepSeekChatModel);
    }

    @Bean(ChatModelNames.GEMINI_FLASH)
    public ChatClient geminiChatClient(ThinkXecGoogleGenAiChatModel googleGenAiChatModel) {
        return chatClient(googleGenAiChatModel);
    }

    @Bean(ChatModelNames.QWEN_FLASH)
    public ChatClient qwenChatClient(OpenAiChatModel qwenChatModel) {
        return chatClient(qwenChatModel);
    }

    /**
     * A client that never executes tools itself: the agent runtime runs them, so it can persist each
     * call and pass the tool context (ADR 040). Options set per request replace these defaults, so a
     * request that sets options must set this again. Nothing else is set here: an agent's temperature
     * and topP are stored but not applied.
     */
    static ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                .build();
    }

    /** The JDK client used by DeepSeek and Qwen: both timeouts apply to every attempt. */
    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        requirePositive(CONNECT_TIMEOUT, connectTimeout);
        requirePositive(READ_TIMEOUT, readTimeout);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    private static void requirePositive(String property, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " must be positive, but is " + value);
        }
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static String firstSet(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

}
