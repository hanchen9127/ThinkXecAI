package com.thinkxecai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.thinkxecai.agent.tool.ToolCallFailure;
import com.thinkxecai.model.ToolFailureKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records why each tool call of one reply failed, if it did (ADR 064). A run's tool callbacks are wrapped so
 * each call notes its kind, null for a success. Spring AI gives a callback no call id, but it runs a reply's
 * calls one at a time in their order (pinned by {@code ToolOutcomesTest}), so the kinds are paired with the
 * results by position. A name the run does not have never reaches a callback: its result is
 * {@link ToolFailureKind#UNKNOWN_TOOL}. One instance belongs to one run, which executes its steps in turn.
 */
@Slf4j
final class ToolOutcomes {

    private final List<ToolCallback> callbacks;
    private final Set<String> names;
    private final List<ToolFailureKind> recorded = new ArrayList<>();

    ToolOutcomes(List<ToolCallback> tools) {
        this.callbacks = tools.stream().<ToolCallback>map(Recording::new).toList();
        this.names = tools.stream().map(tool -> tool.getToolDefinition().name()).collect(Collectors.toUnmodifiableSet());
    }

    /** The run's tool callbacks, each recording its outcome. */
    List<ToolCallback> callbacks() {
        return callbacks;
    }

    /** Forgets the previous reply's calls; called before a reply's calls are executed. */
    void start() {
        recorded.clear();
    }

    /** One kind per result, in the results' order; null where the call succeeded. */
    List<ToolFailureKind> kinds(List<ToolResponseMessage.ToolResponse> results) {
        long known = results.stream().filter(result -> names.contains(result.name())).count();
        if (known != recorded.size()) {
            // Only if Spring AI stopped running calls in order through these callbacks: record nothing rather than guess.
            log.warn("{} tool results for {} recorded outcomes; failures not recorded for this step", known, recorded.size());
            return results.stream().map(result -> names.contains(result.name()) ? null : ToolFailureKind.UNKNOWN_TOOL)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        Iterator<ToolFailureKind> next = recorded.iterator();
        List<ToolFailureKind> kinds = new ArrayList<>(results.size());
        for (ToolResponseMessage.ToolResponse result : results) {
            kinds.add(names.contains(result.name()) ? next.next() : ToolFailureKind.UNKNOWN_TOOL);
        }
        return Collections.unmodifiableList(kinds);
    }

    /** The kind of a failed call; Spring AI wraps what a tool throws, and arguments that are not JSON. */
    static ToolFailureKind kindOf(Throwable cause) {
        if (cause instanceof ToolCallFailure failure) {
            return failure.kind();
        }
        if (cause instanceof JsonProcessingException) {
            return ToolFailureKind.INVALID_ARGUMENTS;
        }
        return ToolFailureKind.TOOL_ERROR;
    }

    private final class Recording implements ToolCallback {

        private final ToolCallback delegate;

        private Recording(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String result;
            try {
                result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            } catch (ToolExecutionException e) {
                recorded.add(kindOf(e.getCause() != null ? e.getCause() : e));
                throw e;
            } catch (RuntimeException | Error e) {
                recorded.add(ToolFailureKind.TOOL_ERROR);
                throw e;
            }
            recorded.add(null);
            return result;
        }
    }

}
