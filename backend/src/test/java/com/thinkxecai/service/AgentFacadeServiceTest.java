package com.thinkxecai.service;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatOptions;
import com.thinkxecai.model.ChatOptionsInput;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFacadeServiceTest extends ServiceTestSupport {

    @Autowired
    private AgentFacadeService service;

    @Test
    void createWithOnlyRequiredFieldsAppliesDefaults() {
        AgentVO created = service.createAgent(createRequest("Research"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Research");
        assertThat(created.getModel()).isEqualTo("deepseek-chat");
        assertThat(created.getAllowedTools()).isEmpty();
        assertThat(created.getAllowedKbs()).isEmpty();
        assertThat(created.getChatOptions()).isEqualTo(ChatOptions.defaults());
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void javaDefaultsMatchTheDatabaseColumnDefault() {
        UUID id = jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model) VALUES ('raw', 'deepseek-chat') RETURNING id", UUID.class);

        AgentVO stored = service.listAgents().stream()
                .filter(agent -> agent.getId().equals(id.toString()))
                .findFirst()
                .orElseThrow();

        assertThat(stored.getChatOptions()).isEqualTo(ChatOptions.defaults());
    }

    @Test
    void createWithPartialChatOptionsFillsTheRestWithDefaults() {
        CreateAgentRequest request = createRequest("Research");
        request.setChatOptions(ChatOptionsInput.builder().temperature(0.3).build());

        AgentVO created = service.createAgent(request);

        assertThat(created.getChatOptions()).isEqualTo(new ChatOptions(0.3, 1.0, 10));
    }

    @Test
    void createStoresKnownKnowledgeBasesWithoutDuplicates() {
        UUID first = insertKnowledgeBase("first");
        UUID second = insertKnowledgeBase("second");
        CreateAgentRequest request = createRequest("Research");
        request.setAllowedKbs(List.of(second, first, second));

        AgentVO created = service.createAgent(request);

        assertThat(created.getAllowedKbs()).containsExactly(second.toString(), first.toString());
    }

    @Test
    void createStoresOptionalToolsWithoutDuplicates() {
        CreateAgentRequest request = createRequest("Research");
        request.setAllowedTools(List.of("emailTool", "dataBaseTool", "emailTool"));

        AgentVO created = service.createAgent(request);

        assertThat(created.getAllowedTools()).containsExactly("emailTool", "dataBaseTool");
    }

    @Test
    void createWithAnUnknownOrFixedToolIsRejectedAndWritesNothing() {
        CreateAgentRequest request = createRequest("Rejected");
        request.setAllowedTools(List.of("dataBaseTool", "databaseQuery", "KnowledgeTool", "databaseQuery"));

        assertThatThrownBy(() -> service.createAgent(request))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("allowedTools: not an optional tool: databaseQuery, KnowledgeTool");
                });
        assertThat(countRows("agent", "name = ?", "Rejected")).isZero();
    }

    @Test
    void toolNamesAreCaseSensitive() {
        CreateAgentRequest request = createRequest("Rejected");
        request.setAllowedTools(List.of("EmailTool"));

        assertThatThrownBy(() -> service.createAgent(request))
                .hasMessage("allowedTools: not an optional tool: EmailTool");
    }

    @Test
    void updateValidatesToolsAndKeepsTheStoredListWhenRejected() {
        CreateAgentRequest create = createRequest("Research");
        create.setAllowedTools(List.of("dataBaseTool"));
        UUID id = UUID.fromString(service.createAgent(create).getId());
        UpdateAgentRequest rejected = new UpdateAgentRequest();
        rejected.setAllowedTools(List.of("emailTool", "terminate"));
        UpdateAgentRequest accepted = new UpdateAgentRequest();
        accepted.setAllowedTools(List.of("emailTool", "emailTool"));

        assertThatThrownBy(() -> service.updateAgent(id, rejected)).hasMessage("allowedTools: not an optional tool: terminate");
        assertThat(service.listAgents()).filteredOn(agent -> agent.getId().equals(id.toString()))
                .singleElement().satisfies(agent -> assertThat(agent.getAllowedTools()).containsExactly("dataBaseTool"));

        assertThat(service.updateAgent(id, accepted).getAllowedTools()).containsExactly("emailTool");
    }

    @Test
    void createWithUnknownKnowledgeBaseIsRejectedAndWritesNothing() {
        UUID unknown = UUID.randomUUID();
        CreateAgentRequest request = createRequest("Rejected");
        request.setAllowedKbs(List.of(insertKnowledgeBase("known"), unknown));

        assertThatThrownBy(() -> service.createAgent(request))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("allowedKbs: unknown knowledge base " + unknown);
                });
        assertThat(countRows("agent", "name = ?", "Rejected")).isZero();
    }

    @Test
    void updateMergesChatOptionsAndKeepsOtherFields() {
        CreateAgentRequest create = createRequest("Research");
        create.setSystemPrompt("Be brief.");
        create.setChatOptions(new ChatOptionsInput(0.7, 0.9, 30));
        UUID id = UUID.fromString(service.createAgent(create).getId());
        UpdateAgentRequest update = new UpdateAgentRequest();
        update.setModel("gemini-3.8-flash");
        update.setChatOptions(ChatOptionsInput.builder().temperature(0.2).build());

        AgentVO updated = service.updateAgent(id, update);

        assertThat(updated.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(updated.getChatOptions()).isEqualTo(new ChatOptions(0.2, 0.9, 30));
        assertThat(updated.getName()).isEqualTo("Research");
        assertThat(updated.getSystemPrompt()).isEqualTo("Be brief.");
    }

    @Test
    void updateWithUnknownKnowledgeBaseIsRejectedAndChangesNothing() {
        UUID kept = insertKnowledgeBase("kept");
        CreateAgentRequest create = createRequest("Research");
        create.setAllowedKbs(List.of(kept));
        UUID id = UUID.fromString(service.createAgent(create).getId());
        UpdateAgentRequest update = new UpdateAgentRequest();
        update.setName("Renamed");
        update.setAllowedKbs(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.updateAgent(id, update))
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(400));

        AgentVO stored = service.listAgents().stream().filter(a -> a.getId().equals(id.toString())).findFirst().orElseThrow();
        assertThat(stored.getName()).isEqualTo("Research");
        assertThat(stored.getAllowedKbs()).containsExactly(kept.toString());
    }

    @Test
    void updateOfUnknownIdIsNotFound() {
        UpdateAgentRequest update = new UpdateAgentRequest();
        update.setName("Renamed");

        assertNotFound(() -> service.updateAgent(UUID.randomUUID(), update));
    }

    @Test
    void deleteKeepsSessionsWithoutAnAgent() {
        UUID id = UUID.fromString(service.createAgent(createRequest("Research")).getId());
        UUID session = insertChatSession(id, "session");

        service.deleteAgent(id);

        assertThat(countRows("agent", "id = ?", id)).isZero();
        assertThat(countRows("chat_session", "id = ? AND agent_id IS NULL", session)).isEqualTo(1);
    }

    @Test
    void deleteOfUnknownIdIsNotFound() {
        assertNotFound(() -> service.deleteAgent(UUID.randomUUID()));
    }

    private static CreateAgentRequest createRequest(String name) {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName(name);
        request.setModel("deepseek-chat");
        return request;
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

}
