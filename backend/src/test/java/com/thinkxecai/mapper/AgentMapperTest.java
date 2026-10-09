package com.thinkxecai.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.entity.AgentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMapperTest extends MapperTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AgentMapper mapper;

    @Test
    void insertReadsBackGeneratedIdAndTimestamps() {
        AgentEntity agent = agent("Research");

        assertThat(mapper.insert(agent)).isEqualTo(1);

        assertThat(agent.getId()).isNotNull();
        assertThat(agent.getCreatedAt()).isNotNull();
        assertThat(agent.getUpdatedAt()).isEqualTo(agent.getCreatedAt());
    }

    @Test
    void jsonbFieldsRoundTrip() {
        AgentEntity agent = agent("Research");
        agent.setAllowedTools("[\"dataBaseTool\",\"emailTool\"]");
        agent.setAllowedKbs("[\"" + UUID.randomUUID() + "\"]");
        agent.setChatOptions("{\"temperature\":0.3,\"topP\":0.9,\"messageLength\":5}");
        mapper.insert(agent);

        AgentEntity found = mapper.selectById(agent.getId());

        // JSONB stores parsed JSON, so whitespace and key order may differ from the input text.
        assertThat(json(found.getAllowedTools())).isEqualTo(json(agent.getAllowedTools()));
        assertThat(json(found.getAllowedKbs())).isEqualTo(json(agent.getAllowedKbs()));
        assertThat(json(found.getChatOptions())).isEqualTo(json(agent.getChatOptions()));
        assertThat(found.getSystemPrompt()).isEqualTo("You are helpful.");
        assertThat(found.getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    void nullJsonbFieldsFallBackToColumnDefaults() {
        AgentEntity agent = AgentEntity.builder().name("Bare").model("deepseek-chat").build();

        mapper.insert(agent);

        AgentEntity found = mapper.selectById(agent.getId());
        assertThat(json(found.getAllowedTools())).isEqualTo(json("[]"));
        assertThat(json(found.getAllowedKbs())).isEqualTo(json("[]"));
        assertThat(json(found.getChatOptions()))
                .isEqualTo(json("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}"));
    }

    @Test
    void selectByIdReturnsNullForUnknownId() {
        assertThat(mapper.selectById(UUID.randomUUID())).isNull();
    }

    @Test
    void listAllOrdersByCreatedAtDescending() {
        UUID oldest = insert("oldest").getId();
        UUID newest = insert("newest").getId();
        UUID middle = insert("middle").getId();
        // now() is fixed within the test transaction, so give the rows distinct timestamps.
        setCreatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setCreatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        List<UUID> ids = mapper.listAll().stream()
                .map(AgentEntity::getId)
                .filter(Set.of(oldest, newest, middle)::contains)
                .toList();

        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    @Test
    void updateChangesOnlyNonNullFieldsAndRefreshesUpdatedAt() {
        AgentEntity agent = insert("Research");
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE agent SET updated_at = ? WHERE id = ?", longAgo, agent.getId());

        int updated = mapper.update(AgentEntity.builder()
                .id(agent.getId())
                .model("gemini-3.8-flash")
                .chatOptions("{\"temperature\":0.2,\"topP\":1.0,\"messageLength\":20}")
                .build());

        AgentEntity found = mapper.selectById(agent.getId());
        assertThat(updated).isEqualTo(1);
        assertThat(found.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(json(found.getChatOptions()).get("messageLength").asInt()).isEqualTo(20);
        assertThat(found.getName()).isEqualTo("Research");
        assertThat(found.getSystemPrompt()).isEqualTo("You are helpful.");
        assertThat(json(found.getAllowedTools())).isEqualTo(json(agent.getAllowedTools()));
        assertThat(found.getCreatedAt()).isEqualTo(agent.getCreatedAt());
        assertThat(found.getUpdatedAt()).isAfter(longAgo);
    }

    @Test
    void deleteKeepsSessionsAndClearsTheirAgent() {
        AgentEntity agent = insert("Research");
        UUID sessionId = insertChatSession(agent.getId(), "session");

        assertThat(mapper.deleteById(agent.getId())).isEqualTo(1);

        assertThat(mapper.selectById(agent.getId())).isNull();
        assertThat(countRows("chat_session", "id = ? AND agent_id IS NULL", sessionId)).isEqualTo(1);
    }

    @Test
    void removeAllowedKbDropsTheIdOnlyFromAgentsThatReferenceIt() {
        UUID removed = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        AgentEntity both = insertWithAllowedKbs("both", "[\"" + kept + "\", \"" + removed + "\"]");
        AgentEntity onlyRemoved = insertWithAllowedKbs("onlyRemoved", "[\"" + removed + "\"]");
        AgentEntity unrelated = insertWithAllowedKbs("unrelated", "[\"" + kept + "\"]");
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE agent SET updated_at = ?", old);

        assertThat(mapper.removeAllowedKb(removed)).isEqualTo(2);

        assertThat(json(mapper.selectById(both.getId()).getAllowedKbs())).isEqualTo(json("[\"" + kept + "\"]"));
        assertThat(json(mapper.selectById(onlyRemoved.getId()).getAllowedKbs())).isEqualTo(json("[]"));
        assertThat(mapper.selectById(both.getId()).getUpdatedAt()).isAfter(old);
        AgentEntity untouched = mapper.selectById(unrelated.getId());
        assertThat(json(untouched.getAllowedKbs())).isEqualTo(json("[\"" + kept + "\"]"));
        assertThat(untouched.getUpdatedAt()).isEqualTo(old);
    }

    private static AgentEntity agent(String name) {
        return AgentEntity.builder()
                .name(name)
                .description(name + " agent")
                .systemPrompt("You are helpful.")
                .model("deepseek-chat")
                .allowedTools("[\"dataBaseTool\"]")
                .allowedKbs("[]")
                .chatOptions("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}")
                .build();
    }

    private AgentEntity insert(String name) {
        AgentEntity agent = agent(name);
        mapper.insert(agent);
        return agent;
    }

    private AgentEntity insertWithAllowedKbs(String name, String allowedKbs) {
        AgentEntity agent = agent(name);
        agent.setAllowedKbs(allowedKbs);
        mapper.insert(agent);
        return agent;
    }

    private void setCreatedAt(UUID id, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE agent SET created_at = ? WHERE id = ?", createdAt, id);
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not JSON: " + text, e);
        }
    }

}
