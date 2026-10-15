package com.thinkxecai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.agent.tool.Tool;
import com.thinkxecai.agent.tool.ToolType;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.impl.ToolFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolFacadeServiceTest extends ServiceTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIXED_METHODS = List.of("KnowledgeTool", "getCity", "getDate", "terminate", "weather");

    @Autowired
    private ToolFacadeService service;

    @Test
    void theApplicationHasTheSpecifiedFixedAndOptionalTools() {
        assertThat(service.listFixedTools()).extracting(ToolVO::getName)
                .containsExactly("KnowledgeTool", "cityTool", "dateTool", "terminate", "weatherTool");
        assertThat(service.listOptionalTools()).extracting(ToolVO::getName)
                .containsExactly("dataBaseTool", "emailTool");
        assertThat(service.listTools()).extracting(ToolVO::getName)
                .containsExactly("KnowledgeTool", "cityTool", "dateTool", "terminate", "weatherTool", "dataBaseTool", "emailTool");
    }

    @Test
    void optionalToolsNeverIncludeAFixedTool() {
        assertThat(service.listOptionalTools()).isNotEmpty()
                .allSatisfy(tool -> assertThat(tool.getType()).isEqualTo(ToolType.OPTIONAL))
                .allSatisfy(tool -> assertThat(tool.getDescription()).isNotBlank());
    }

    @Test
    void everyAgentGetsAllFixedToolsAndOnlyTheOptionalOnesItLists() {
        assertThat(modelFacingNames(service.toolCallbacksFor(List.of()))).containsExactlyElementsOf(FIXED_METHODS);
        assertThat(modelFacingNames(service.toolCallbacksFor(null))).containsExactlyElementsOf(FIXED_METHODS);
        assertThat(modelFacingNames(service.toolCallbacksFor(List.of("emailTool"))))
                .containsExactlyElementsOf(plus(FIXED_METHODS, "sendEmail"));
        assertThat(modelFacingNames(service.toolCallbacksFor(List.of("emailTool", "dataBaseTool"))))
                .containsExactlyElementsOf(plus(FIXED_METHODS, "databaseQuery", "sendEmail"));
    }

    /** Rows saved before allowedTools was validated may hold anything; the agent still runs with what is valid. */
    @Test
    void duplicateUnknownFixedAndNullNamesAreSkippedWithoutDuplicates() {
        List<String> stored = Arrays.asList("dataBaseTool", "dataBaseTool", "databaseQuery", "KnowledgeTool", null, "");

        assertThat(modelFacingNames(service.toolCallbacksFor(stored)))
                .containsExactlyElementsOf(plus(FIXED_METHODS, "databaseQuery"))
                .doesNotHaveDuplicates();
    }

    /** The runtime's tool context (allowed knowledge bases) must never become a model-facing argument. */
    @Test
    void noInputSchemaExposesTheToolContext() throws Exception {
        for (ToolCallback callback : service.toolCallbacksFor(List.of("emailTool", "dataBaseTool"))) {
            JsonNode properties = JSON.readTree(callback.getToolDefinition().inputSchema()).path("properties");
            List<String> arguments = new ArrayList<>();
            properties.fieldNames().forEachRemaining(arguments::add);

            assertThat(arguments).as(callback.getToolDefinition().name()).doesNotContain("toolContext");
            assertThat(callback.getToolDefinition().inputSchema()).doesNotContainIgnoringCase("ToolContext");
        }
    }

    @Test
    void toolsAreGroupedByTypeAndSortedByName() {
        ToolFacadeService stubbed = new ToolFacadeServiceImpl(List.of(
                tool("zeta", ToolType.OPTIONAL), tool("beta", ToolType.FIXED),
                tool("alpha", ToolType.OPTIONAL), tool("gamma", ToolType.FIXED)));

        assertThat(stubbed.listTools()).extracting(ToolVO::getName).containsExactly("beta", "gamma", "alpha", "zeta");
        assertThat(stubbed.listFixedTools()).extracting(ToolVO::getName).containsExactly("beta", "gamma");
        assertThat(stubbed.listOptionalTools()).extracting(ToolVO::getName).containsExactly("alpha", "zeta");
    }

    @Test
    void twoToolsWithOneNameStopStartup() {
        List<Tool> tools = List.of(tool("same", ToolType.FIXED), tool("same", ToolType.OPTIONAL), tool("other", ToolType.FIXED));

        assertThatThrownBy(() -> new ToolFacadeServiceImpl(tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate tool names: same");
    }

    private static Tool tool(String name, ToolType type) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name + " description";
            }

            @Override
            public ToolType getType() {
                return type;
            }
        };
    }

    private static List<String> modelFacingNames(List<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
    }

    private static List<String> plus(List<String> names, String... more) {
        List<String> all = new ArrayList<>(names);
        all.addAll(List.of(more));
        return all;
    }

}
