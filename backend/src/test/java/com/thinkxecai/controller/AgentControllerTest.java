package com.thinkxecai.controller;

import com.thinkxecai.model.ChatOptions;
import com.thinkxecai.model.ChatOptionsInput;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;
import com.thinkxecai.service.AgentFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paths, validation, and the exact {@code data} shape of each endpoint (ISSUE-008); the service is mocked. */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    private static final UUID ID = UUID.fromString("0b6a1f2e-5c3d-4e7f-8a90-123456789abc");
    private static final UUID KB = UUID.fromString("7f8c2c1e-3a5b-4d6e-9f01-23456789abcd");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentFacadeService service;

    @Test
    void listReturnsAnArrayOfAgents() throws Exception {
        when(service.listAgents()).thenReturn(List.of(vo("Research")));

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Research"))
                .andExpect(jsonPath("$.data[0].description").value("Research agent"))
                .andExpect(jsonPath("$.data[0].systemPrompt").value("Be brief."))
                .andExpect(jsonPath("$.data[0].model").value("deepseek-chat"))
                .andExpect(jsonPath("$.data[0].allowedTools[0]").value("databaseQuery"))
                .andExpect(jsonPath("$.data[0].allowedKbs[0]").value(KB.toString()))
                .andExpect(jsonPath("$.data[0].chatOptions.temperature").value(0.7))
                .andExpect(jsonPath("$.data[0].chatOptions.topP").value(1.0))
                .andExpect(jsonPath("$.data[0].chatOptions.messageLength").value(10))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-10-07T12:00:00"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-10-07T12:00:00"));
    }

    @Test
    void createReturnsTheAgentObject() throws Exception {
        when(service.createAgent(any())).thenReturn(vo("Research"));

        send(post("/api/agents"), """
                {"name": "Research", "model": "deepseek-chat", "allowedKbs": ["%s"],
                 "chatOptions": {"messageLength": 20}}""".formatted(KB))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.chatOptions.messageLength").value(10));

        verify(service).createAgent(argThat((CreateAgentRequest request) ->
                request.getAllowedKbs().equals(List.of(KB))
                        && request.getChatOptions().equals(ChatOptionsInput.builder().messageLength(20).build())));
    }

    @Test
    void createRequiresNameAndAKnownModel() throws Exception {
        send(post("/api/agents"), "{\"model\": \"deepseek-chat\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
        send(post("/api/agents"), "{\"name\": \"Research\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("model: must not be null"));
        send(post("/api/agents"), "{\"name\": \"Research\", \"model\": \"gpt-4o\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("model: must be deepseek-chat or gemini-3.8-flash"));

        verifyNoInteractions(service);
    }

    @Test
    void chatOptionsOutOfRangeAreRejected() throws Exception {
        send(post("/api/agents"), """
                {"name": "Research", "model": "deepseek-chat",
                 "chatOptions": {"temperature": 2.5, "topP": -0.1, "messageLength": 0}}""")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("chatOptions.messageLength: must be between 1 and 100; "
                        + "chatOptions.temperature: must be between 0 and 2; "
                        + "chatOptions.topP: must be between 0 and 1"));
        send(patch("/api/agents/" + ID), "{\"chatOptions\": {\"messageLength\": 101}}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("chatOptions.messageLength: must be between 1 and 100"));

        verifyNoInteractions(service);
    }

    @Test
    void allowedKbsMustBeUuids() throws Exception {
        send(post("/api/agents"), "{\"name\": \"Research\", \"model\": \"deepseek-chat\", \"allowedKbs\": [\"kb-1\"]}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("allowedKbs[0]: invalid value"));
        send(post("/api/agents"), "{\"name\": \"Research\", \"model\": \"deepseek-chat\", \"allowedKbs\": [null]}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("allowedKbs[0]: must not be null"));

        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsTheAgentObject() throws Exception {
        when(service.updateAgent(eq(ID), any())).thenReturn(vo("Renamed"));

        send(patch("/api/agents/" + ID), "{\"name\": \"Renamed\", \"model\": \"gemini-3.8-flash\"}")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Renamed"));

        verify(service).updateAgent(eq(ID), argThat((UpdateAgentRequest request) ->
                request.getName().equals("Renamed") && request.getModel().equals("gemini-3.8-flash")
                        && request.getChatOptions() == null));
    }

    @Test
    void updateRejectsBlankNameAndUnknownModel() throws Exception {
        send(patch("/api/agents/" + ID), "{\"name\": \" \"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
        send(patch("/api/agents/" + ID), "{\"model\": \"gpt-4o\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("model: must be deepseek-chat or gemini-3.8-flash"));

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsNullData() throws Exception {
        mockMvc.perform(delete("/api/agents/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(service).deleteAgent(ID);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private static AgentVO vo(String name) {
        return AgentVO.builder()
                .id(ID.toString())
                .name(name)
                .description(name + " agent")
                .systemPrompt("Be brief.")
                .model("deepseek-chat")
                .allowedTools(List.of("databaseQuery"))
                .allowedKbs(List.of(KB.toString()))
                .chatOptions(ChatOptions.defaults())
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

}
