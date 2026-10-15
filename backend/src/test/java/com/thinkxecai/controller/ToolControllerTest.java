package com.thinkxecai.controller;

import com.thinkxecai.agent.tool.ToolType;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.ToolFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The exact {@code data} shape of {@code GET /api/tools} (ISSUE-008); the service is mocked. */
@WebMvcTest(ToolController.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToolFacadeService service;

    @Test
    void listReturnsTheOptionalToolsOnly() throws Exception {
        when(service.listOptionalTools()).thenReturn(List.of(
                new ToolVO("dataBaseTool", "Runs read-only SQL.", ToolType.OPTIONAL)));

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("dataBaseTool"))
                .andExpect(jsonPath("$.data[0].description").value("Runs read-only SQL."))
                .andExpect(jsonPath("$.data[0].type").value("OPTIONAL"))
                .andExpect(jsonPath("$.data[0].*", hasSize(3)));

        verify(service).listOptionalTools();
        verifyNoMoreInteractions(service);
    }

}
