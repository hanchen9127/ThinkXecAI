package com.thinkxecai.agent.tool;

import com.thinkxecai.model.ToolFailureKind;

import java.util.Objects;

/**
 * Thrown by a tool when a call did not do what was asked (ADR 064). The message is exactly what the model
 * is told, so it can correct itself; the kind is stored with the result. It never ends the run.
 */
public class ToolCallFailure extends RuntimeException {

    private final ToolFailureKind kind;

    public ToolCallFailure(ToolFailureKind kind, String message) {
        super(message, null, false, false);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public ToolFailureKind kind() {
        return kind;
    }

}
