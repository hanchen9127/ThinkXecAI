package com.thinkxecai.agent;

import com.thinkxecai.agent.tool.ToolCallFailure;
import com.thinkxecai.model.ToolFailureKind;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.stream.Stream;

import static com.thinkxecai.agent.ScriptedChatModel.call;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why each call failed is recorded in call order and paired with the results by position (ADR 064). This
 * runs Spring AI's own tool calling manager, so a change in how it executes a reply's calls shows here.
 */
class ToolOutcomesTest {

    private final ToolOutcomes outcomes = new ToolOutcomes(
            List.of(MethodToolCallbackProvider.builder().toolObjects(new Probe()).build().getToolCallbacks()));
    private final ToolCallingManager manager = ToolCallingManager.builder()
            .toolExecutionExceptionProcessor(AgentRuntimeFactory::toolFailed)
            .build();

    @Test
    void eachResultGetsTheKindOfItsOwnCallInOrder() {
        List<ToolResponseMessage.ToolResponse> results = execute(
                call("1", "refuse", "{}"), call("2", "ok", "{}"), call("3", "broken", "{}"), call("4", "ok", "{\"x\":"));

        assertThat(outcomes.kinds(results)).containsExactly(
                ToolFailureKind.REFUSED, null, ToolFailureKind.TOOL_ERROR, ToolFailureKind.INVALID_ARGUMENTS);
        assertThat(results).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("1", "2", "3", "4");
    }

    @Test
    void theModelGetsAToolsOwnFailureTextUnchanged() {
        List<ToolResponseMessage.ToolResponse> results = execute(call("1", "refuse", "{}"), call("2", "broken", "{}"));

        assertThat(results).extracting(ToolResponseMessage.ToolResponse::responseData)
                .containsExactly("Not allowed here.", "Tool broken failed: IllegalStateException.");
    }

    @Test
    void eachReplyStartsAfresh() {
        execute(call("1", "ok", "{}"));

        List<ToolResponseMessage.ToolResponse> next = execute(call("2", "refuse", "{}"));

        // the earlier reply's success must not shift this reply's failure off its result
        assertThat(outcomes.kinds(next)).containsExactly(ToolFailureKind.REFUSED);
    }

    @Test
    void aNameTheRunDoesNotHaveIsAnUnknownToolBetweenTheOthers() {
        execute(call("1", "ok", "{}"), call("3", "refuse", "{}"));
        List<ToolResponseMessage.ToolResponse> results = List.of(
                new ToolResponseMessage.ToolResponse("1", "ok", "\"fine\""),
                new ToolResponseMessage.ToolResponse("2", "databaseQuery", "Unknown tool"),
                new ToolResponseMessage.ToolResponse("3", "refuse", "Not allowed here."));

        assertThat(outcomes.kinds(results))
                .containsExactly(null, ToolFailureKind.UNKNOWN_TOOL, ToolFailureKind.REFUSED);
    }

    @Test
    void resultsThatDoNotMatchTheRecordedCallsRecordNoFailureRatherThanAWrongOne() {
        execute(call("1", "refuse", "{}"));
        List<ToolResponseMessage.ToolResponse> results = List.of(
                new ToolResponseMessage.ToolResponse("1", "refuse", "x"),
                new ToolResponseMessage.ToolResponse("2", "ok", "y"),
                new ToolResponseMessage.ToolResponse("3", "databaseQuery", "z"));

        assertThat(outcomes.kinds(results)).containsExactly(null, null, ToolFailureKind.UNKNOWN_TOOL);
    }

    @Test
    void theWrappedCallbacksKeepTheToolsDefinitions() {
        assertThat(outcomes.callbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("ok", "refuse", "broken");
    }

    private List<ToolResponseMessage.ToolResponse> execute(AssistantMessage.ToolCall... calls) {
        List<ToolCallback> callbacks = outcomes.callbacks();
        Prompt prompt = new Prompt(List.of(), ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks).internalToolExecutionEnabled(false).build());
        ChatResponse response = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(Stream.of(calls).toList()).build())));
        outcomes.start();
        var history = manager.executeToolCalls(prompt, response).conversationHistory();
        return ((ToolResponseMessage) history.get(history.size() - 1)).getResponses();
    }

    static class Probe {

        @Tool(name = "ok", description = "Succeeds.")
        public String ok() {
            return "fine";
        }

        @Tool(name = "refuse", description = "Declines by rule.")
        public String refuse() {
            throw new ToolCallFailure(ToolFailureKind.REFUSED, "Not allowed here.");
        }

        @Tool(name = "broken", description = "Fails.")
        public String broken() {
            throw new IllegalStateException("boom");
        }
    }

}
