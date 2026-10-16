package com.thinkxecai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exports the OpenAPI document generated from the controllers to backend/target/openapi.json. CI
 * regenerates the frontend's API types from that file and fails when they drift from ui/src/api.
 * The assertions check that the document lists exactly the specified endpoints and describes
 * response fields the way Jackson writes them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    static final Path OUTPUT = Path.of("target", "openapi.json");

    /** Every JSON endpoint in tech-stack.md (SSE is not JSON and is not described). */
    private static final Map<String, List<String>> ENDPOINTS = new TreeMap<>(Map.ofEntries(
            Map.entry("/api/agents", List.of("get", "post")),
            Map.entry("/api/agents/{agentId}", List.of("delete", "patch")),
            Map.entry("/api/chat-sessions", List.of("get", "post")),
            Map.entry("/api/chat-sessions/{chatSessionId}", List.of("delete", "get", "patch")),
            Map.entry("/api/chat-sessions/agent/{agentId}", List.of("get")),
            Map.entry("/api/chat-messages", List.of("post")),
            Map.entry("/api/chat-messages/{chatMessageId}", List.of("delete")),
            Map.entry("/api/chat-messages/session/{sessionId}", List.of("get")),
            Map.entry("/api/knowledge-bases", List.of("get", "post")),
            Map.entry("/api/knowledge-bases/{knowledgeBaseId}", List.of("delete", "patch")),
            Map.entry("/api/documents", List.of("get", "post")),
            Map.entry("/api/documents/{documentId}", List.of("delete", "patch")),
            Map.entry("/api/documents/kb/{kbId}", List.of("get")),
            Map.entry("/api/documents/upload", List.of("post")),
            Map.entry("/api/tools", List.of("get"))));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode document;

    @BeforeEach
    void exportTheOpenApiDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        document = objectMapper.readTree(body);

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n");
    }

    @Test
    void describesExactlyTheSpecifiedEndpoints() {
        Map<String, List<String>> described = new TreeMap<>();
        document.get("paths").fields().forEachRemaining(path -> {
            List<String> methods = new ArrayList<>();
            path.getValue().fieldNames().forEachRemaining(methods::add);
            described.put(path.getKey(), methods.stream().sorted().toList());
        });

        assertThat(described).isEqualTo(ENDPOINTS);
    }

    @Test
    void responseFieldsAreRequiredAndNullOnlyWhereAnnotated() {
        JsonNode agent = schema("AgentVO");
        assertThat(names(agent.get("required"))).containsExactlyInAnyOrderElementsOf(names(agent.get("properties")));
        assertThat(agent.at("/properties/name/type").asText()).isEqualTo("string");
        assertThat(names(agent.at("/properties/description/type"))).containsExactly("string", "null");

        JsonNode message = schema("ChatMessageVO");
        assertThat(message.at("/properties/metadata/oneOf/0/$ref").asText()).isEqualTo("#/components/schemas/ChatMessageMetadata");
        assertThat(message.at("/properties/metadata/oneOf/1/type").asText()).isEqualTo("null");
        // the null belongs to the field, not to the shared type it refers to
        JsonNode metadata = schema("ChatMessageMetadata");
        assertThat(names(metadata.get("type"))).containsExactly("object");
        assertThat(names(metadata.get("properties"))).containsExactlyInAnyOrder("toolCalls", "toolResponse");

        assertThat(names(schema("ChatOptions").get("required"))).containsExactlyInAnyOrder("temperature", "topP", "messageLength");

        assertThat(names(agent.at("/properties/model/enum"))).containsExactly("deepseek-chat", "gemini-3.8-flash");
        assertThat(names(schema("ApiResponseVoid").get("required"))).containsExactlyInAnyOrder("code", "message", "data");

        JsonNode tool = schema("ToolVO");
        assertThat(names(tool.get("required"))).containsExactlyInAnyOrder("name", "description", "type");
        assertThat(names(tool.at("/properties/type/enum"))).containsExactly("FIXED", "OPTIONAL");
    }

    @Test
    void requestSchemasKeepTheirValidationRules() {
        assertThat(names(schema("CreateAgentRequest").get("required"))).containsExactlyInAnyOrder("name", "model");
        assertThat(schema("CreateAgentRequest").at("/properties/chatOptions/$ref").asText())
                .isEqualTo("#/components/schemas/ChatOptionsInput");
        assertThat(schema("ChatOptionsInput").has("required")).isFalse();
        assertThat(names(schema("CreateAgentRequest").at("/properties/model/enum"))).containsExactly("deepseek-chat", "gemini-3.8-flash");
        assertThat(schema("UpdateChatSessionRequest").get("properties").has("agentId")).isFalse();
        assertThat(schema("CreateDocumentRequest").get("properties").has("metadata")).isFalse();
    }

    private JsonNode schema(String name) {
        JsonNode schema = document.at("/components/schemas/" + name);
        assertThat(schema.isMissingNode()).as("schema %s", name).isFalse();
        return schema;
    }

    /** Field names of an object node, or the text values of an array node. */
    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node == null || node.isMissingNode()) {
            return names;
        }
        if (node.isArray()) {
            node.forEach(element -> names.add(element.asText()));
        } else if (node.isObject()) {
            node.fieldNames().forEachRemaining(names::add);
        } else {
            names.add(node.asText());
        }
        return names;
    }

}
