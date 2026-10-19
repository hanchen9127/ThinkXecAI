package com.thinkxecai.agent.memory;

import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.ToolResponseMetadata;
import com.thinkxecai.model.dto.ChatMessageDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes a window of stored messages valid model input (ISSUE-003 B). The window is cut at an
 * arbitrary row, and a run that died between saving a tool call and its result leaves a call without
 * a result; both models reject such a history (a tool result must answer a call of the assistant
 * message directly before it, and every call needs its result).
 * <p>
 * Rules, applied in order of the rows:
 * <ul>
 *   <li>stored {@code system} rows are skipped (the runtime never writes them);</li>
 *   <li>rows before the first {@code user} row are dropped;</li>
 *   <li>a {@code tool} row is kept only if it answers a not yet answered call of the assistant message
 *       it directly follows (only other results of that message in between);</li>
 *   <li>an assistant message keeps only the calls that got a result; with none left it stays as plain
 *       text when it has text, and is dropped otherwise.</li>
 * </ul>
 */
public final class MemorySanitizer {

    private MemorySanitizer() {
    }

    public static List<ChatMessageDTO> sanitize(List<ChatMessageDTO> messages) {
        List<ChatMessageDTO> kept = new ArrayList<>();
        ToolStep step = null;
        boolean started = false;
        for (ChatMessageDTO message : messages) {
            ChatMessageRole role = message.getRole();
            if (role == ChatMessageRole.SYSTEM) {
                continue;
            }
            if (!started && role != ChatMessageRole.USER) {
                continue;
            }
            started = true;
            if (role == ChatMessageRole.TOOL) {
                if (step != null) {
                    step.answer(message);
                }
                continue;
            }
            if (step != null) {
                step.closeInto(kept);
                step = null;
            }
            if (role == ChatMessageRole.ASSISTANT && !toolCalls(message).isEmpty()) {
                step = new ToolStep(message);
            } else {
                kept.add(message);
            }
        }
        if (step != null) {
            step.closeInto(kept);
        }
        return kept;
    }

    private static List<ToolCallMetadata> toolCalls(ChatMessageDTO message) {
        ChatMessageMetadata metadata = message.getMetadata();
        return metadata == null || metadata.toolCalls() == null ? List.of() : metadata.toolCalls();
    }

    /** An assistant message with tool calls and the results that directly follow it. */
    private static final class ToolStep {

        private final ChatMessageDTO assistant;
        private final Set<String> callIds = new HashSet<>();
        private final Map<String, ChatMessageDTO> results = new LinkedHashMap<>();

        ToolStep(ChatMessageDTO assistant) {
            this.assistant = assistant;
            for (ToolCallMetadata call : toolCalls(assistant)) {
                if (call != null && call.id() != null && !call.id().isBlank()) {
                    callIds.add(call.id());
                }
            }
        }

        void answer(ChatMessageDTO tool) {
            ToolResponseMetadata response = tool.getMetadata() == null ? null : tool.getMetadata().toolResponse();
            if (response != null && callIds.contains(response.id())) {
                results.putIfAbsent(response.id(), tool);
            }
        }

        void closeInto(List<ChatMessageDTO> kept) {
            List<ToolCallMetadata> answeredCalls = toolCalls(assistant).stream()
                    .filter(call -> call != null && results.containsKey(call.id()))
                    .toList();
            if (!answeredCalls.isEmpty()) {
                kept.add(withCalls(answeredCalls));
                kept.addAll(results.values());
            } else if (assistant.getContent() != null && !assistant.getContent().isBlank()) {
                kept.add(withCalls(List.of()));
            }
        }

        private ChatMessageDTO withCalls(List<ToolCallMetadata> calls) {
            if (calls.size() == toolCalls(assistant).size()) {
                return assistant;
            }
            return ChatMessageDTO.builder()
                    .id(assistant.getId())
                    .sessionId(assistant.getSessionId())
                    .role(assistant.getRole())
                    .content(assistant.getContent())
                    .metadata(calls.isEmpty() ? null : new ChatMessageMetadata(calls, null))
                    .createdAt(assistant.getCreatedAt())
                    .updatedAt(assistant.getUpdatedAt())
                    .build();
        }

    }

}
