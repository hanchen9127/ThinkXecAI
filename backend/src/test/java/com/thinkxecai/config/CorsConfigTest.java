package com.thinkxecai.config;

import com.thinkxecai.controller.DiagnosticController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Local origins (the Vite dev server) may call the API; credentials are never allowed (ADR 033). */
@WebMvcTest(DiagnosticController.class)
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aLocalOriginIsAllowedWithoutCredentials() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:4173"}) {
            mockMvc.perform(preflight(origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
    }

    @Test
    void anotherOriginIsRefused() throws Exception {
        mockMvc.perform(preflight("http://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preflight(String origin) {
        return options("/health")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH");
    }

}
