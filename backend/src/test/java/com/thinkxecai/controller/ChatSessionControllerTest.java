package com.thinkxecai.controller;

import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;
import com.thinkxecai.service.ChatSessionFacadeService;
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
@WebMvcTest(ChatSessionController.class)
class ChatSessionControllerTest {

    private static final UUID ID = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");
    private static final UUID AGENT = UUID.fromString("0b6a1f2e-5c3d-4e7f-8a90-123456789abc");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 8, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatSessionFacadeService service;

    @Test
    void listReturnsAnArrayOfSessions() throws Exception {
        when(service.listChatSessions()).thenReturn(List.of(vo("First chat")));

        mockMvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].agentId").value(AGENT.toString()))
                .andExpect(jsonPath("$.data[0].title").value("First chat"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-10-08T09:30:00"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-10-08T09:30:00"));
    }

    @Test
    void getReturnsTheSessionObject() throws Exception {
        when(service.getChatSession(ID)).thenReturn(vo("First chat"));

        mockMvc.perform(get("/api/chat-sessions/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.title").value("First chat"));
    }

    @Test
    void listByAgentReturnsAnArray() throws Exception {
        when(service.listChatSessionsByAgent(AGENT)).thenReturn(List.of(vo("First chat")));

        mockMvc.perform(get("/api/chat-sessions/agent/" + AGENT))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentId").value(AGENT.toString()));
    }

    @Test
    void createReturnsTheSessionObject() throws Exception {
        when(service.createChatSession(any())).thenReturn(vo("First chat"));

        send(post("/api/chat-sessions"), "{\"agentId\": \"%s\", \"title\": \"First chat\"}".formatted(AGENT))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()));

        verify(service).createChatSession(argThat((CreateChatSessionRequest request) ->
                AGENT.equals(request.getAgentId()) && "First chat".equals(request.getTitle())));
    }

    @Test
    void createRejectsNonUuidAgentAndBlankTitle() throws Exception {
        send(post("/api/chat-sessions"), "{\"agentId\": \"agent-1\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("agentId: invalid value"));
        send(post("/api/chat-sessions"), "{\"title\": \" \"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title: must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsTheSessionObject() throws Exception {
        when(service.updateChatSession(eq(ID), any())).thenReturn(vo("Renamed"));

        send(patch("/api/chat-sessions/" + ID), "{\"title\": \"Renamed\"}")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Renamed"));

        verify(service).updateChatSession(eq(ID), argThat((UpdateChatSessionRequest request) ->
                "Renamed".equals(request.getTitle())));
    }

    @Test
    void nonUuidPathIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/chat-sessions/42"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("chatSessionId: must be a UUID"));

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsNullData() throws Exception {
        mockMvc.perform(delete("/api/chat-sessions/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(service).deleteChatSession(ID);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private static ChatSessionVO vo(String title) {
        return ChatSessionVO.builder()
                .id(ID.toString())
                .agentId(AGENT.toString())
                .title(title)
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

}
