package com.thinkxecai.config;

import com.thinkxecai.controller.DiagnosticController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only local host names reach the application (ADR 033); a DNS-rebinding page sends its own name. */
@WebMvcTest(DiagnosticController.class)
class HostCheckFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "localhost:8080", "127.0.0.1:8080", "127.0.0.1:5173", "LOCALHOST:8080"})
    void localHostNamesAreServed(String host) throws Exception {
        mockMvc.perform(get("/health").header(HttpHeaders.HOST, host))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.example", "evil.example:8080", "localhost.evil.example:8080",
            "127.0.0.1.nip.io:8080", "192.168.1.5:8080", "[::1]:8080"})
    void otherHostNamesAreRefusedBeforeAnyController(String host) throws Exception {
        for (String path : new String[]{"/health", "/api/agents"}) {
            mockMvc.perform(get(path).header(HttpHeaders.HOST, host))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("Host not allowed"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

}
