package com.thinkxecai.config;

import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the configured chat models without calling any API. The Gemini model must be set explicitly:
 * Spring AI's default (gemini-2.0-flash) has been shut down, and falling back to it would only fail at
 * the first real request. Qwen goes through Spring AI's OpenAI module without its auto-configuration, so
 * it adds no model besides Qwen's and nothing reads {@code spring.ai.openai.*}; Ollama stays the only
 * embedding model (ADR 050). Qwen's endpoint is pinned here because the HTTP tests replace it.
 */
@SpringBootTest
class ChatModelConfigurationTest {

    @Autowired
    private Map<String, ChatModel> chatModels;

    @Autowired
    private Map<String, EmbeddingModel> embeddingModels;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    @Autowired
    private ThinkXecGoogleGenAiChatModel geminiChatModel;

    @Autowired
    private OpenAiChatModel qwenChatModel;

    @Autowired
    private Map<String, OpenAiApi> openAiApis;

    @Autowired
    private ApplicationContext context;

    @Value("${thinkxecai.chat.qwen.base-url}")
    private String qwenBaseUrl;

    @Test
    void exactlyDeepSeekGeminiAndQwenAreConfigured() {
        assertThat(chatModels.values())
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(DeepSeekChatModel.class, ThinkXecGoogleGenAiChatModel.class,
                        OpenAiChatModel.class);
    }

    @Test
    void eachChatModelUsesTheModelNamedInTheSpec() {
        assertThat(deepSeekChatModel.getDefaultOptions().getModel()).isEqualTo("deepseek-chat");
        assertThat(geminiChatModel.getDefaultOptions().getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(qwenChatModel.getDefaultOptions().getModel()).isEqualTo("qwen3.8-flash");
    }

    @Test
    void qwenCallsTheInternationalCompatibleEndpointWithItsModelsOwnSampling() {
        // The international site is the only one that accepts the project's key; Spring AI appends /v1/chat/completions.
        assertThat(qwenBaseUrl).isEqualTo("https://dashscope-intl.aliyuncs.com/compatible-mode");
        assertThat(qwenChatModel.getDefaultOptions().getTemperature()).isNull();
        assertThat(qwenChatModel.getDefaultOptions().getTopP()).isNull();
    }

    @Test
    void theOpenAiModuleAddsNothingButQwensClient() {
        assertThat(ClassUtils.isPresent(
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration", null)).isFalse();
        assertThat(embeddingModels.values()).singleElement().isInstanceOf(OllamaEmbeddingModel.class);
        assertThat(openAiApis).containsOnlyKeys("qwenOpenAiApi");
        assertThat(context.getBeanNamesForType(OpenAiImageModel.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenAiAudioSpeechModel.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenAiAudioTranscriptionModel.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenAiModerationModel.class)).isEmpty();
    }

}
