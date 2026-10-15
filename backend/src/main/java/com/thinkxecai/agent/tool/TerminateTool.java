package com.thinkxecai.agent.tool;

import org.springframework.stereotype.Component;

/** Ends the agent loop; the runtime (Phase 11) stops when it sees this call. */
@Component
public class TerminateTool implements Tool {

    @Override
    public String getName() {
        return "terminate";
    }

    @Override
    public String getDescription() {
        return "Ends the agent loop when the task is complete.";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "terminate",
            description = "Call this once every task the user asked for is complete. It ends the agent loop.")
    public void terminate() {
    }

}
