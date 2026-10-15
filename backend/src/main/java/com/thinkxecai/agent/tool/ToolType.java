package com.thinkxecai.agent.tool;

public enum ToolType {
    /** Given to every agent. */
    FIXED,
    /** Given only to agents that list it in {@code allowedTools}. */
    OPTIONAL
}
