package com.thinkxecai.config;

import com.thinkxecai.agent.model.ThinkXecGoogleGenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two configured chat models without calling either API. The Gemini model must be set
 * explicitly: Spring AI's default (gemini-2.0-flash) has been shut down, and falling back to it
 * would only fail at the first real request.
 */
@SpringBootTest
class ChatModelConfigurationTest {

    @Autowired
    private Map<String, ChatModel> chatModels;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    @Autowired
    private ThinkXecGoogleGenAiChatModel geminiChatModel;

    @Test
    void exactlyDeepSeekAndTheApplicationsGeminiModelAreConfigured() {
        assertThat(chatModels.values())
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(DeepSeekChatModel.class, ThinkXecGoogleGenAiChatModel.class);
    }

    @Test
    void eachChatModelUsesTheModelNamedInTheSpec() {
        assertThat(deepSeekChatModel.getDefaultOptions().getModel()).isEqualTo("deepseek-chat");
        assertThat(geminiChatModel.getDefaultOptions().getModel()).isEqualTo("gemini-3.8-flash");
    }

}
