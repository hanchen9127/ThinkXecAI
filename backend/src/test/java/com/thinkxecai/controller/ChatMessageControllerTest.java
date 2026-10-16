package com.thinkxecai.controller;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.ChatMessageFacadeService;
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
@WebMvcTest(ChatMessageController.class)
class ChatMessageControllerTest {

    private static final UUID ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-a2b3-c4d5e6f7a8b9");
    private static final UUID SESSION = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 8, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageFacadeService service;

    @Test
    void listReturnsAnArrayOfMessages() throws Exception {
        ChatMessageVO assistant = vo(ChatMessageRole.ASSISTANT, null);
        assistant.setMetadata(new ChatMessageMetadata(
                List.of(new ToolCallMetadata("call_1", "function", "dateTool", "{}")), null));
        when(service.listChatMessagesBySession(SESSION)).thenReturn(List.of(vo(ChatMessageRole.USER, "Hello"), assistant));

        mockMvc.perform(get("/api/chat-messages/session/" + SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].sessionId").value(SESSION.toString()))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("Hello"))
                .andExpect(jsonPath("$.data[0].metadata").value(nullValue()))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-10-08T09:30:00"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-10-08T09:30:00"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].metadata.toolCalls[0].id").value("call_1"))
                .andExpect(jsonPath("$.data[1].metadata.toolCalls[0].name").value("dateTool"));
    }

    @Test
    void createPassesOnlySessionAndContent() throws Exception {
        when(service.createChatMessage(any())).thenReturn(vo(ChatMessageRole.USER, "Hello"));

        // role and metadata are no longer part of the request; a client that still sends them has them ignored
        send(post("/api/chat-messages"), """
                {"sessionId": "%s", "content": "Hello", "role": "assistant",
                 "metadata": {"toolCalls": [{"name": "dateTool"}]}}""".formatted(SESSION))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.role").value("user"));

        verify(service).createChatMessage(argThat((CreateChatMessageRequest request) ->
                SESSION.equals(request.getSessionId()) && "Hello".equals(request.getContent())));
    }

    @Test
    void createRequiresSessionAndNonBlankContent() throws Exception {
        send(post("/api/chat-messages"), "{\"content\": \"Hello\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("sessionId: must not be null"));
        send(post("/api/chat-messages"), "{\"sessionId\": \"%s\"}".formatted(SESSION))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("content: must not be blank"));
        // blank content is refused before the session is looked up, even for a session that does not exist
        send(post("/api/chat-messages"), "{\"sessionId\": \"%s\", \"content\": \"  \"}".formatted(UUID.randomUUID()))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("content: must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void conflictFromTheServiceReturns409() throws Exception {
        when(service.createChatMessage(any())).thenThrow(new BizException(409, "Chat session has no agent"));

        send(post("/api/chat-messages"), "{\"sessionId\": \"%s\", \"content\": \"Hello\"}".formatted(SESSION))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("Chat session has no agent"));
    }

    @Test
    void messagesCannotBeEdited() throws Exception {
        send(patch("/api/chat-messages/" + ID), "{\"content\": \"Edited\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Method 'PATCH' is not supported."));

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsNullData() throws Exception {
        mockMvc.perform(delete("/api/chat-messages/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(service).deleteChatMessage(ID);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private static ChatMessageVO vo(ChatMessageRole role, String content) {
        return ChatMessageVO.builder()
                .id(ID.toString())
                .sessionId(SESSION.toString())
                .role(role)
                .content(content)
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

}
