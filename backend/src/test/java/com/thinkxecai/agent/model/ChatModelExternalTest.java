package com.thinkxecai.agent.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real DeepSeek and Qwen APIs through the registry (needs real
 * {@code THINKXECAI_DEEPSEEK_API_KEY} and {@code THINKXECAI_QWEN_API_KEY}; costs a few requests).
 * The tool round trip answers the model's call by hand, as the agent runtime will, and sends the
 * original assistant message back. The shape of the first answer is logged, since only a real
 * response shows whether a model mixes text and a call. Gemini is no longer called here: its free
 * tier answered too slowly to test (ADR 050).
 */
@Tag("external")
@SpringBootTest
class ChatModelExternalTest {

    private static final Logger log = LoggerFactory.getLogger(ChatModelExternalTest.class);
    private static final String DATE = "2026-10-19";

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void eachModelAnswers(String model) {
        String answer = chatClientRegistry.get(model).prompt()
                .user("Reply with exactly the word OK and nothing else.")
                .call()
                .content();

        log.info("{} answered: {}", model, answer);
        assertThat(answer).containsIgnoringCase("ok");
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void aToolCallCanBeAnsweredAndTheConversationContinues(String model) {
        ChatClient chatClient = chatClientRegistry.get(model);
        AtomicInteger toolRuns = new AtomicInteger();
        ToolCallback getDate = dateTool(toolRuns);
        List<Message> conversation = List.of(
                new SystemMessage("Before you call a tool, say in one short sentence what you are about to do."),
                new UserMessage("What is today's date? Use the getDate tool."));

        ChatResponse first = chatClient.prompt().messages(conversation).toolCallbacks(getDate).call().chatResponse();

        AssistantMessage call = first.getResult().getOutput();
        log.info("{} first answer: generations={}, text=\"{}\", toolCalls={}, thoughtSignatures={}", model,
                first.getResults().size(), call.getText(), call.getToolCalls(),
                call.getMetadata().get("thoughtSignatures") instanceof List<?> signatures ? signatures.size() : 0);
        assertThat(call.getToolCalls()).extracting(AssistantMessage.ToolCall::name).containsExactly("getDate");

        AssistantMessage.ToolCall toolCall = call.getToolCalls().get(0);
        ToolResponseMessage result = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
                        "{\"date\":\"" + DATE + "\"}")))
                .build();
        List<Message> continued = List.of(conversation.get(0), conversation.get(1), call, result);

        ChatResponse second = chatClient.prompt().messages(continued).toolCallbacks(getDate).call().chatResponse();

        String answer = second.getResult().getOutput().getText();
        log.info("{} final answer: {}", model, answer);
        assertThat(second.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(answer).containsAnyOf(DATE, "19");
        assertThat(toolRuns).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "qwen3.8-flash"})
    void aConversationWithToolCallsCanBeAnsweredWithoutTools(String model) {
        // ADR 045: after a terminate without text the runtime asks once more, offering no tools
        ChatClient chatClient = chatClientRegistry.get(model);
        AtomicInteger toolRuns = new AtomicInteger();
        List<Message> conversation = List.of(
                new SystemMessage("Answer briefly."),
                new UserMessage("What is today's date? Use the getDate tool."));
        AssistantMessage call = chatClient.prompt().messages(conversation).toolCallbacks(dateTool(toolRuns))
                .call().chatResponse().getResult().getOutput();
        assertThat(call.getToolCalls()).extracting(AssistantMessage.ToolCall::name).containsExactly("getDate");
        AssistantMessage.ToolCall toolCall = call.getToolCalls().get(0);
        ToolResponseMessage result = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "\"" + DATE + "\"")))
                .build();

        String answer = chatClient.prompt().messages(List.of(conversation.get(0), conversation.get(1), call, result))
                .call().content();

        log.info("{} answered without tools: {}", model, answer);
        assertThat(answer).containsAnyOf(DATE, "19");
    }

    private static ToolCallback dateTool(AtomicInteger runs) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("getDate")
                        .description("Returns today's date as yyyy-MM-dd.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                runs.incrementAndGet();
                return DATE;
            }
        };
    }

}
