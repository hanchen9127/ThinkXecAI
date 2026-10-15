package com.thinkxecai.service;

import com.thinkxecai.agent.tool.Tool;
import com.thinkxecai.agent.tool.ToolType;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.impl.ToolFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolFacadeServiceTest extends ServiceTestSupport {

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

}
