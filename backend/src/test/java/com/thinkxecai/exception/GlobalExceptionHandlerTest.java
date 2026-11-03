package com.thinkxecai.exception;

import com.thinkxecai.model.ChatMessageRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    private static final String SECRET_DETAIL = "relation \"agent\" violates constraint at com.thinkxecai.Secret";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void failedValidationReturns400NamingTheField() throws Exception {
        postBody("{\"name\": \" \", \"role\": \"user\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith("name: ")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void severalFailedFieldsAreAllNamed() throws Exception {
        postBody("{}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith("name: ")))
                .andExpect(jsonPath("$.message").value(containsString("; role: ")));
    }

    @Test
    void validationMessagesAreEnglishWhateverTheRequestLanguage() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content("{\"name\": \" \", \"role\": \"user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postBody("{\"name\": ")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body"));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body"));
    }

    @Test
    void unknownEnumValueReturns400NamingTheField() throws Exception {
        postBody("{\"name\": \"a\", \"role\": \"robot\"}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("role: invalid value"));
    }

    @Test
    void invalidValueInsideAListNamesTheElement() throws Exception {
        postBody("{\"name\": \"a\", \"role\": \"user\", \"ids\": [\"not-a-uuid\"]}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("ids[0]: invalid value"));
    }

    @Test
    void nonUuidPathVariableReturns400() throws Exception {
        mockMvc.perform(get("/test/errors/items/not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id: must be a UUID"));
    }

    @Test
    void clientBizExceptionKeepsItsCodeAndMessage() throws Exception {
        mockMvc.perform(get("/test/errors/items/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Item not found"));
        mockMvc.perform(delete("/test/errors/conflict"))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("Item is in use"));
    }

    @Test
    void aServiceThatAnswersTooLateIs503SayingToTryAgain() throws Exception {
        mockMvc.perform(get("/test/errors/service/read-timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.SERVICE_TIMED_OUT_MESSAGE));
    }

    @Test
    void aServiceThatCannotBeReachedIs503SayingToCheckIt() throws Exception {
        for (String failure : new String[] {"refused", "connect-timeout"}) {
            mockMvc.perform(get("/test/errors/service/" + failure))
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.SERVICE_UNREACHABLE_MESSAGE));
        }
    }

    /** The client's message carries the service's URL; it goes only to the log. */
    @Test
    void aServiceFailureDoesNotExposeTheServiceAddress() throws Exception {
        mockMvc.perform(get("/test/errors/service/refused"))
                .andExpect(jsonPath("$.message").value(not(containsString("11434"))));
    }

    @Test
    void bizExceptionWithoutCodeReturnsGeneric500() throws Exception {
        mockMvc.perform(get("/test/errors/internal-biz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
                .andExpect(jsonPath("$.message").value(not(containsString("Secret"))));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutDetails() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/test/errors/no-such-endpoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void unsupportedMethodReturns400() throws Exception {
        mockMvc.perform(put("/test/errors/unexpected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unsupportedContentTypeReturns400() throws Exception {
        mockMvc.perform(post("/test/errors/body").contentType(MediaType.TEXT_PLAIN).content("name=a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    record TestRequest(@NotBlank String name, @NotNull ChatMessageRole role, List<UUID> ids) {
    }

    /** Test-only endpoints that raise each kind of error. Nested in a test class, so never component-scanned. */
    @RestController
    @RequestMapping("/test/errors")
    static class TestController {

        @PostMapping("/body")
        String body(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable UUID id) {
            throw new BizException(404, "Item not found");
        }

        @DeleteMapping("/conflict")
        String conflict() {
            throw new BizException(409, "Item is in use");
        }

        /** The exceptions Spring's HTTP clients throw over the JDK client when a service does not answer. */
        @GetMapping("/service/{failure}")
        String service(@PathVariable String failure) {
            IOException cause = switch (failure) {
                case "read-timeout" -> new HttpTimeoutException("Request cancelled");
                case "connect-timeout" -> new HttpConnectTimeoutException("HTTP connect timed out");
                default -> new ConnectException();
            };
            throw new ResourceAccessException("I/O error on POST request for \"http://localhost:11434/api/embed\"", cause);
        }

        @GetMapping("/internal-biz")
        String internalBiz() {
            throw new BizException("Failed to parse agent JSON field: " + SECRET_DETAIL);
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException(SECRET_DETAIL);
        }
    }

}
