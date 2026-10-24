package com.thinkxecai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A zero timeout would switch Gemini's timeout off and make every DeepSeek or Qwen call fail at once, so
 * both settings must be positive; the check runs before anything else in the bean methods (ADR 039).
 */
class ChatModelTimeoutSettingsTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private final ChatModelConfig config = new ChatModelConfig();

    private void buildGeminiClient(Duration readTimeout) {
        GoogleGenAiConnectionProperties properties = new GoogleGenAiConnectionProperties();
        properties.setApiKey("test-key");
        config.googleGenAiClient(properties, new SpringAiRetryProperties(), "http://127.0.0.1:1", readTimeout);
    }

    private void buildDeepSeekModel(Duration connectTimeout, Duration readTimeout) {
        config.deepSeekChatModel(null, null, null, null, null, null, null, null, null, connectTimeout, readTimeout);
    }

    private void buildQwenApi(Duration connectTimeout, Duration readTimeout) {
        config.qwenOpenAiApi(null, null, null, null, connectTimeout, readTimeout);
    }

    @Test
    void geminiRefusesAZeroOrNegativeReadTimeout() {
        assertThatThrownBy(() -> buildGeminiClient(Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("thinkxecai.chat.read-timeout must be positive, but is PT0S");
        assertThatThrownBy(() -> buildGeminiClient(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void geminiAcceptsAPositiveReadTimeout() {
        buildGeminiClient(ONE_SECOND);
    }

    @Test
    void deepSeekRefusesAZeroConnectTimeout() {
        assertThatThrownBy(() -> buildDeepSeekModel(Duration.ZERO, ONE_SECOND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("thinkxecai.chat.connect-timeout must be positive, but is PT0S");
    }

    @Test
    void deepSeekRefusesAZeroOrNegativeReadTimeout() {
        assertThatThrownBy(() -> buildDeepSeekModel(ONE_SECOND, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("thinkxecai.chat.read-timeout must be positive, but is PT0S");
        assertThatThrownBy(() -> buildDeepSeekModel(ONE_SECOND, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void qwenRefusesAZeroConnectOrReadTimeout() {
        assertThatThrownBy(() -> buildQwenApi(Duration.ZERO, ONE_SECOND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("thinkxecai.chat.connect-timeout must be positive, but is PT0S");
        assertThatThrownBy(() -> buildQwenApi(ONE_SECOND, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("thinkxecai.chat.read-timeout must be positive, but is PT-0.001S");
    }

}
