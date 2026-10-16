package com.thinkxecai.controller;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;
import com.thinkxecai.service.KnowledgeBaseFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {

    private static final UUID ID = UUID.fromString("7f8c2c1e-3a5b-4d6e-9f01-23456789abcd");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseFacadeService service;

    @Test
    void listReturnsAnArrayOfKnowledgeBases() throws Exception {
        when(service.listKnowledgeBases()).thenReturn(List.of(vo("Docs")));

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Docs"))
                .andExpect(jsonPath("$.data[0].description").value("Docs description"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-10-07T12:00:00"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-10-07T12:00:00"));
    }

    @Test
    void createReturnsTheKnowledgeBaseObject() throws Exception {
        when(service.createKnowledgeBase(any())).thenReturn(vo("Docs"));

        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Docs\", \"description\": \"Docs description\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.name").value("Docs"));

        verify(service).createKnowledgeBase(argThat((CreateKnowledgeBaseRequest request) ->
                request.getName().equals("Docs") && request.getDescription().equals("Docs description")));
    }

    @Test
    void createWithBlankNameIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void aNulCharacterInAnyTextIsRejectedBeforeTheService() throws Exception {
        // PostgreSQL cannot store U+0000 in text or jsonb; without this check the insert failed with a 500
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Docs\", \"description\": \"a\\u0000b\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("description: invalid value"));

        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsTheKnowledgeBaseObject() throws Exception {
        when(service.updateKnowledgeBase(eq(ID), any())).thenReturn(vo("Manuals"));

        mockMvc.perform(patch("/api/knowledge-bases/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Manuals\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Manuals"));

        verify(service).updateKnowledgeBase(eq(ID), argThat((UpdateKnowledgeBaseRequest request) ->
                request.getName().equals("Manuals") && request.getDescription() == null));
    }

    @Test
    void updateWithoutNameIsAllowedButBlankNameIsRejected() throws Exception {
        when(service.updateKnowledgeBase(eq(ID), any())).thenReturn(vo("Docs"));

        mockMvc.perform(patch("/api/knowledge-bases/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"new\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(patch("/api/knowledge-bases/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void updateWithNonUuidIdIsRejected() throws Exception {
        mockMvc.perform(patch("/api/knowledge-bases/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Manuals\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("knowledgeBaseId: must be a UUID"));

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsNullData() throws Exception {
        mockMvc.perform(delete("/api/knowledge-bases/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(service).deleteKnowledgeBase(ID);
    }

    @Test
    void notFoundFromTheServiceReturns404() throws Exception {
        doThrow(new BizException(404, "Knowledge base not found")).when(service).deleteKnowledgeBase(ID);

        mockMvc.perform(delete("/api/knowledge-bases/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Knowledge base not found"));
    }

    private static KnowledgeBaseVO vo(String name) {
        return KnowledgeBaseVO.builder()
                .id(ID.toString())
                .name(name)
                .description(name + " description")
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

}
