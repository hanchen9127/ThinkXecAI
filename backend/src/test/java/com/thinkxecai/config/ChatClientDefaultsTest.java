package com.thinkxecai.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The prompt a registered chat client hands to its model asks Spring AI not to execute tools, and
 * carries no sampling options of its own (ADR 040).
 */
class ChatClientDefaultsTest {

    private final ChatModel chatModel = mock(ChatModel.class);

    private Prompt promptSentBy(ChatClient chatClient) {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("ok").build()))));

        assertThat(chatClient.prompt("hi").call().content()).isEqualTo("ok");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        return prompt.getValue();
    }

    @Test
    void theClientAsksTheModelNotToExecuteTools() {
        Prompt prompt = promptSentBy(ChatModelConfig.chatClient(chatModel));

        assertThat(prompt.getOptions()).isInstanceOfSatisfying(ToolCallingChatOptions.class, options ->
                assertThat(options.getInternalToolExecutionEnabled()).isFalse());
    }

    @Test
    void theClientSetsNoTemperatureOrTopP() {
        Prompt prompt = promptSentBy(ChatModelConfig.chatClient(chatModel));

        assertThat(prompt.getOptions().getTemperature()).isNull();
        assertThat(prompt.getOptions().getTopP()).isNull();
        assertThat(prompt.getOptions().getModel()).isNull();
    }

}
