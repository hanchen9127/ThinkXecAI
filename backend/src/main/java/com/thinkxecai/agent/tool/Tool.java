package com.thinkxecai.agent.tool;

/**
 * A tool an agent can call. Each implementation is a Spring bean with exactly one Spring AI
 * {@code @Tool} method; {@link #getName()} is the name agents store in {@code allowedTools}, which
 * can differ from the method name the model sees.
 */
public interface Tool {

    String getName();

    /** The name people see for this tool everywhere in the UI (ADR 065), in place of either code name. */
    String getLabel();

    /** Shown to people choosing tools for an agent; the model reads the {@code @Tool} description. */
    String getDescription();

    ToolType getType();

    /** Whether {@code GET /api/tools} lists it; false for a tool that only runs the loop. */
    default boolean isShown() {
        return true;
    }

}
