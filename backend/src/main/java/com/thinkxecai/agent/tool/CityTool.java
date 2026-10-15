package com.thinkxecai.agent.tool;

import org.springframework.stereotype.Component;

/**
 * Test tool with a fixed answer, for trying out tool calling. It does not look up the user's real
 * location, and its answer says so, so the model does not present Sydney as where the user is.
 */
@Component
public class CityTool implements Tool {

    @Override
    public String getName() {
        return "cityTool";
    }

    @Override
    public String getDescription() {
        return "Returns a fixed sample city (test tool, always Sydney; no real location lookup).";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getCity", description = "Get a sample city. Test tool: it does not know the user's real location.")
    public String getCity() {
        return "Sydney (sample value, not the user's real location)";
    }

}
