package com.thinkxecai.agent.tool;

import org.springframework.stereotype.Component;

/** Ends the agent loop; the runtime stops when it sees this call (AgentRuntime). */
@Component
public class TerminateTool implements Tool {

    /** The stored name and the name the model calls. */
    public static final String NAME = "terminate";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Ends the agent loop when the task is complete.";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = NAME,
            description = "Call this once every task the user asked for is complete. It ends the agent loop.")
    public void terminate() {
    }

}
