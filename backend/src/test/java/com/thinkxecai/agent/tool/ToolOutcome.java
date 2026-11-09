package com.thinkxecai.agent.tool;

import java.util.function.Supplier;

/** A tool call's outcome as one string, for assertions: the result, or {@code "<KIND>: <text for the model>"}. */
final class ToolOutcome {

    private ToolOutcome() {
    }

    static String of(Supplier<String> call) {
        try {
            return call.get();
        } catch (ToolCallFailure failure) {
            return failure.kind() + ": " + failure.getMessage();
        }
    }

}
