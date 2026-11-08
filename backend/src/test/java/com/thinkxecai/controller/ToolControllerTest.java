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
    void listReturnsEveryShownToolWithItsLabelAndCallName() throws Exception {
        when(service.listTools()).thenReturn(List.of(
                new ToolVO("dateTool", "getDate", "Date", "Returns today's date.", ToolType.FIXED),
                new ToolVO("dataBaseTool", "databaseQuery", "Database", "Runs read-only SQL.", ToolType.OPTIONAL)));

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].type").value("FIXED"))
                .andExpect(jsonPath("$.data[1].name").value("dataBaseTool"))
                .andExpect(jsonPath("$.data[1].callName").value("databaseQuery"))
                .andExpect(jsonPath("$.data[1].label").value("Database"))
                .andExpect(jsonPath("$.data[1].description").value("Runs read-only SQL."))
                .andExpect(jsonPath("$.data[1].type").value("OPTIONAL"))
                .andExpect(jsonPath("$.data[1].*", hasSize(5)));

        verify(service).listTools();
        verifyNoMoreInteractions(service);
    }

}
