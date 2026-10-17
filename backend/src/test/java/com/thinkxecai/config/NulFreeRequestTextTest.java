package com.thinkxecai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.controller.KnowledgeBaseController;
import com.thinkxecai.service.KnowledgeBaseFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * NUL characters are refused in request bodies only (ADR 036). The application's shared ObjectMapper
 * also reads other JSON, such as chat-model replies, which must not fail because a reply contains one.
 */
@WebMvcTest(KnowledgeBaseController.class)
class NulFreeRequestTextTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KnowledgeBaseFacadeService service;

    @Test
    void aRequestBodyWithANulCharacterIsRefused() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"a\\u0000b\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: invalid value"));

        verifyNoInteractions(service);
    }

    @Test
    void theSharedObjectMapperStillReadsNulCharacters() throws Exception {
        assertThat(objectMapper.readValue("\"a\\u0000b\"", String.class)).isEqualTo("a\u0000b");
    }

}
