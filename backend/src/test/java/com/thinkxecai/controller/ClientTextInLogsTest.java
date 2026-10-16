package com.thinkxecai.controller;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.service.AgentFacadeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Client text in a logged error message cannot start a fake log line (review R5A-5). */
@WebMvcTest(AgentController.class)
@ExtendWith(OutputCaptureExtension.class)
class ClientTextInLogsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentFacadeService service;

    @Test
    void lineBreaksFromTheClientAreEscapedInTheLog(CapturedOutput output) throws Exception {
        // the message names the client's tool name, as the real service does for an unknown tool
        when(service.createAgent(any())).thenThrow(
                new BizException(400, "allowedTools: not an optional tool: x\n2026-10-17 ERROR forged line"));

        mockMvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"a\", \"model\": \"deepseek-chat\"}"))
                .andExpect(jsonPath("$.code").value(400));

        assertThat(output.getOut()).contains("x\\n2026-10-17 ERROR forged line").doesNotContain("\n2026-10-17 ERROR forged");
    }

}
