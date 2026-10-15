package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The agent's allowed knowledge bases come only from the tool context, never from the model (ISSUE-006). */
class KnowledgeToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID KB_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID KB_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final RagService ragService = mock(RagService.class);
    private final KnowledgeTool tool = new KnowledgeTool(ragService);

    private static ToolContext allowing(UUID... kbIds) {
        return new ToolContext(Map.of(KnowledgeTool.ALLOWED_KB_IDS, List.of(kbIds).stream().map(UUID::toString).toList()));
    }

    @Test
    void anAllowedKnowledgeBaseIsSearchedAndTheSectionsAreJoined() {
        when(ragService.search(KB_A, "setup")).thenReturn(List.of("first section", "second section"));

        String answer = tool.knowledgeQuery(KB_A.toString(), "setup", allowing(KB_A, KB_B));

        assertThat(answer).isEqualTo("first section\n\nsecond section");
    }

    @Test
    void theIdIsComparedAsAUuidSoCaseAndSpacesDoNotMatter() {
        when(ragService.search(KB_A, "setup")).thenReturn(List.of("section"));

        assertThat(tool.knowledgeQuery(" " + KB_A.toString().toUpperCase() + " ", "setup", allowing(KB_A)))
                .isEqualTo("section");
    }

    @Test
    void aKnowledgeBaseOutsideTheAgentsListIsNotSearched() {
        String answer = tool.knowledgeQuery(KB_B.toString(), "setup", allowing(KB_A));

        assertThat(answer).contains(KB_B + " is not available", "Available knowledge bases: " + KB_A);
        verifyNoInteractions(ragService);
    }

    @Test
    void aMalformedIdIsNotSearched() {
        String answer = tool.knowledgeQuery("kb-1; DROP TABLE chunk_bge_m3", "setup", allowing(KB_A));

        assertThat(answer).contains("is not available", KB_A.toString());
        verifyNoInteractions(ragService);
    }

    @Test
    void withoutAllowedIdsNothingIsSearched() {
        assertThat(tool.knowledgeQuery(KB_A.toString(), "setup", null)).contains("This agent has no knowledge bases.");
        assertThat(tool.knowledgeQuery(KB_A.toString(), "setup", new ToolContext(Map.of("other", "value"))))
                .contains("This agent has no knowledge bases.");
        assertThat(tool.knowledgeQuery(KB_A.toString(), "setup", allowing()))
                .contains("This agent has no knowledge bases.");
        verifyNoInteractions(ragService);
    }

    @Test
    void aBlankQueryIsNotEmbedded() {
        assertThat(tool.knowledgeQuery(KB_A.toString(), " ", allowing(KB_A))).isEqualTo("The query must not be blank.");
        verifyNoInteractions(ragService);
    }

    @Test
    void noResultsAreReportedExplicitly() {
        when(ragService.search(any(), any())).thenReturn(List.of());

        assertThat(tool.knowledgeQuery(KB_A.toString(), "setup", allowing(KB_A)))
                .isEqualTo("No matching content found in knowledge base " + KB_A + ".");
    }

    @Test
    void theModelSeesOnlyKbsIdAndQuery() throws Exception {
        ToolCallback callback = callback();
        JsonNode schema = JSON.readTree(callback.getToolDefinition().inputSchema());

        assertThat(callback.getToolDefinition().name()).isEqualTo("KnowledgeTool");
        assertThat(schema.get("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("kbsId", "query");
    }

    @Test
    void theToolContextReachesTheMethodThroughSpringAi() {
        when(ragService.search(KB_A, "setup")).thenReturn(List.of("section"));
        ToolCallback callback = callback();

        String allowed = callback.call(input(KB_A), allowing(KB_A));
        String foreign = callback.call(input(KB_B), allowing(KB_A));

        assertThat(allowed).contains("section");
        assertThat(foreign).contains("is not available");
        verify(ragService).search(KB_A, "setup");
    }

    /** Pins the Spring AI behavior the Phase 11 runtime must respect: always pass a non-empty context. */
    @Test
    void springAiRefusesTheCallWithoutAToolContext() {
        ToolCallback callback = callback();

        assertThatThrownBy(() -> callback.call(input(KB_A))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> callback.call(input(KB_A), new ToolContext(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ragService);
    }

    private ToolCallback callback() {
        return MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks()[0];
    }

    private static String input(UUID kbId) {
        return "{\"kbsId\":\"" + kbId + "\",\"query\":\"setup\"}";
    }

}
