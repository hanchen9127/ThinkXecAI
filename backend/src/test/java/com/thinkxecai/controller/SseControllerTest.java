package com.thinkxecai.controller;

import com.thinkxecai.exception.BizException;
import com.thinkxecai.model.vo.ChatSessionVO;
import com.thinkxecai.service.ChatSessionFacadeService;
import com.thinkxecai.service.SseService;
import com.thinkxecai.service.impl.SseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The stream opens only for an existing session; otherwise a real HTTP status with the envelope as JSON, as
 * an EventSource asks for text/event-stream (ADR 047). The Host check and CORS cover the path as well.
 */
@WebMvcTest(SseController.class)
@Import(SseServiceImpl.class)
class SseControllerTest {

    private static final UUID SESSION = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");
    private static final String PATH = "/sse/connect/" + SESSION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseService sseService;

    @MockitoBean
    private ChatSessionFacadeService chatSessionFacadeService;

    @AfterEach
    void closeStream() {
        sseService.close(SESSION);
    }

    private ResultActions connect(String path) throws Exception {
        return mockMvc.perform(get(path).accept(MediaType.TEXT_EVENT_STREAM));
    }

    private void sessionExists() {
        when(chatSessionFacadeService.getChatSession(SESSION)).thenReturn(new ChatSessionVO());
    }

    @Test
    void anExistingSessionsStreamStartsWithInitAndLastsThirtyMinutes() throws Exception {
        sessionExists();

        MvcResult result = connect(PATH)
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("event:init\ndata:connected\n\n");
        // the emitter's timeout, not the shorter default of an async request
        assertThat(result.getRequest().getAsyncContext().getTimeout()).isEqualTo(SseService.TIMEOUT.toMillis());
    }

    @Test
    void anUnknownSessionIsARealNotFoundWithTheEnvelope() throws Exception {
        when(chatSessionFacadeService.getChatSession(SESSION)).thenThrow(new BizException(404, "Chat session not found"));

        connect(PATH)
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Chat session not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void anIdThatIsNotAUuidIsARealBadRequestWithTheEnvelope() throws Exception {
        connect("/sse/connect/not-a-uuid")
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("chatSessionId: must be a UUID"));

        verifyNoInteractions(chatSessionFacadeService);
    }

    @Test
    void aFailedLookupIsARealServerErrorWithoutDetails() throws Exception {
        when(chatSessionFacadeService.getChatSession(SESSION))
                .thenThrow(new DataAccessResourceFailureException("connection refused to 127.0.0.1:5433"));

        connect(PATH)
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void onlyGetOpensAStream() throws Exception {
        sessionExists();

        // Spring MVC refuses the method before the controller, through the usual envelope (HTTP 200).
        mockMvc.perform(post(PATH))
                .andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Method 'POST' is not supported."));

        verifyNoInteractions(chatSessionFacadeService);
    }

    @Test
    void aForeignHostIsRefusedBeforeTheController() throws Exception {
        mockMvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM).header(HttpHeaders.HOST, "evil.example:8080"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(chatSessionFacadeService);
    }

    @Test
    void theViteDevServerMayOpenTheStream() throws Exception {
        sessionExists();

        mockMvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void anotherOriginMayNot() throws Exception {
        sessionExists();

        mockMvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM).header(HttpHeaders.ORIGIN, "http://evil.example"))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isForbidden());

        verifyNoInteractions(chatSessionFacadeService);
    }

}
