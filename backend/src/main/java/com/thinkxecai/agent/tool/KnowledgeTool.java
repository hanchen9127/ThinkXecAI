package com.thinkxecai.agent.tool;

import com.thinkxecai.service.RagService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Searches one of the running agent's knowledge bases. The agent's {@code allowedKbs} arrive in the
 * {@link ToolContext}, which is not part of the schema the model sees, so the model cannot widen them:
 * any other or malformed id gets an explanatory answer instead of a search (ISSUE-006).
 * <p>
 * Spring AI refuses to call this method without a non-empty tool context (it throws before the call),
 * so the runtime must always pass {@link #ALLOWED_KB_IDS}, even for an agent without knowledge bases.
 */
@Component
public class KnowledgeTool implements Tool {

    /** Tool context key: the running agent's allowed knowledge-base ids, as strings or UUIDs. */
    public static final String ALLOWED_KB_IDS = "allowedKbIds";

    private final RagService ragService;

    public KnowledgeTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "KnowledgeTool";
    }

    @Override
    public String getDescription() {
        return "Searches the agent's knowledge bases for the sections most relevant to a query.";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "KnowledgeTool",
            description = "Search one of your knowledge bases and return the sections most relevant to the query.")
    public String knowledgeQuery(@ToolParam(description = "Id of a knowledge base available to you") String kbsId,
                                 @ToolParam(description = "What to look for") String query,
                                 ToolContext toolContext) {
        Set<UUID> allowed = allowedKbIds(toolContext);
        UUID kbId = parseUuid(kbsId);
        if (kbId == null || !allowed.contains(kbId)) {
            return notAvailable(kbsId, allowed);
        }
        if (query == null || query.isBlank()) {
            return "The query must not be blank.";
        }
        List<String> contents = ragService.search(kbId, query);
        if (contents.isEmpty()) {
            return "No matching content found in knowledge base " + kbId + ".";
        }
        return String.join("\n\n", contents);
    }

    /** Missing context or key means no knowledge base is allowed; unparsable entries are skipped. */
    private static Set<UUID> allowedKbIds(ToolContext toolContext) {
        if (toolContext == null || !(toolContext.getContext().get(ALLOWED_KB_IDS) instanceof Collection<?> ids)) {
            return Set.of();
        }
        Set<UUID> allowed = new LinkedHashSet<>();
        for (Object id : ids) {
            UUID uuid = id == null ? null : parseUuid(id.toString());
            if (uuid != null) {
                allowed.add(uuid);
            }
        }
        return allowed;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String notAvailable(String kbsId, Set<UUID> allowed) {
        String message = "Knowledge base " + kbsId + " is not available to this agent.";
        if (allowed.isEmpty()) {
            return message + " This agent has no knowledge bases.";
        }
        return message + " Available knowledge bases: "
                + allowed.stream().map(UUID::toString).collect(Collectors.joining(", ")) + ".";
    }

}
