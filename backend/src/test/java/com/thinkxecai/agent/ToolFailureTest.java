package com.thinkxecai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the model is told when a tool fails (ADR 041); the run's own handling is in {@link AgentRunnerTest}. */
class ToolFailureTest {

    private static final ToolDefinition TOOL = ToolDefinition.builder()
            .name("KnowledgeTool").description("Searches.").inputSchema("{\"type\":\"object\"}").build();

    @Test
    void theModelGetsTheToolAndTheFailuresTypeOnly() {
        String text = AgentRuntimeFactory.toolFailed(
                new ToolExecutionException(TOOL, new IllegalStateException("Ollama at 127.0.0.1:11434 refused")));

        assertThat(text).isEqualTo("Tool KnowledgeTool failed: IllegalStateException.");
    }

    @Test
    void anErrorStillEndsTheRun() {
        StackOverflowError error = new StackOverflowError();

        assertThatThrownBy(() -> AgentRuntimeFactory.toolFailed(new ToolExecutionException(TOOL, error))).isSameAs(error);
    }

}
