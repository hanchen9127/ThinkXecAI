package com.thinkxecai.controller;

import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import com.thinkxecai.service.DocumentFacadeService;
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
import java.util.Map;
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
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    private static final UUID ID = UUID.fromString("9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d");
    private static final UUID KB = UUID.fromString("7f8c2c1e-3a5b-4d6e-9f01-23456789abcd");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 8, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentFacadeService service;

    @Test
    void listReturnsAnArrayOfDocuments() throws Exception {
        when(service.listDocuments()).thenReturn(List.of(vo("guide.md")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].kbId").value(KB.toString()))
                .andExpect(jsonPath("$.data[0].name").value("guide.md"))
                .andExpect(jsonPath("$.data[0].metadata.filePath").value("kb/doc/file.md"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-10-08T09:30:00"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-10-08T09:30:00"));
    }

    @Test
    void listByKbReturnsAnArray() throws Exception {
        when(service.listDocumentsByKb(KB)).thenReturn(List.of(vo("guide.md")));

        mockMvc.perform(get("/api/documents/kb/" + KB))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void createReturnsTheDocumentObject() throws Exception {
        when(service.createDocument(any())).thenReturn(vo("guide.md"));

        send(post("/api/documents"), "{\"kbId\": \"%s\", \"name\": \"guide.md\"}".formatted(KB))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ID.toString()));

        verify(service).createDocument(argThat((CreateDocumentRequest request) ->
                KB.equals(request.getKbId()) && "guide.md".equals(request.getName())));
    }

    @Test
    void createRequiresKbIdAndName() throws Exception {
        send(post("/api/documents"), "{\"name\": \"guide.md\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("kbId: must not be null"));
        send(post("/api/documents"), "{\"kbId\": \"%s\", \"name\": \"\"}".formatted(KB))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsTheDocumentObject() throws Exception {
        when(service.updateDocument(eq(ID), any())).thenReturn(vo("renamed.md"));

        send(patch("/api/documents/" + ID), "{\"name\": \"renamed.md\"}")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("renamed.md"));

        verify(service).updateDocument(eq(ID), argThat((UpdateDocumentRequest request) ->
                "renamed.md".equals(request.getName())));
    }

    @Test
    void updateRejectsBlankName() throws Exception {
        send(patch("/api/documents/" + ID), "{\"name\": \" \"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsNullData() throws Exception {
        mockMvc.perform(delete("/api/documents/" + ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(service).deleteDocument(ID);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private static DocumentVO vo(String name) {
        return DocumentVO.builder()
                .id(ID.toString())
                .kbId(KB.toString())
                .name(name)
                .metadata(Map.of("filePath", "kb/doc/file.md"))
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

}
