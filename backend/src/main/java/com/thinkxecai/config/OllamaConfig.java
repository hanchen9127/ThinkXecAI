package com.thinkxecai.config;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Replaces Spring AI's auto-configured {@link OllamaApi} (which backs off when this bean exists) only
 * to give Ollama's HTTP client timeouts. Without them a hanging Ollama held an upload, and since the
 * knowledge tool searches through it, an agent's tool call, forever. Only Ollama's client is changed;
 * the chat models keep their own (ADR 036).
 */
@Configuration
public class OllamaConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails,
                               ObjectProvider<RestClient.Builder> restClientBuilder,
                               ResponseErrorHandler responseErrorHandler,
                               @Value("${thinkxecai.ollama.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(readTimeout);
        return OllamaApi.builder()
                .baseUrl(connectionDetails.getBaseUrl())
                .restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder).requestFactory(requestFactory))
                .responseErrorHandler(responseErrorHandler)
                .build();
    }

}
