package com.thinkxecai.agent.tool;

import org.springframework.stereotype.Component;

/** Ends the agent loop; the runtime stops when it sees this call (AgentRuntime). */
@Component
public class TerminateTool implements Tool {

    /** The stored name and the name the model calls. */
    public static final String NAME = "terminate";

    /** The result instead of ending the run when the calling message has no text: the user would get no answer. */
    public static final String NOT_ENDED = "Not ended: the user sees only your text and has no answer yet. "
            + "Write your final answer to the user as text.";

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
            description = "Ends the agent loop once every task the user asked for is complete. The user sees only your "
                    + "text, never tool results: write your final answer in the same message that calls this.")
    public void terminate() {
    }

}
