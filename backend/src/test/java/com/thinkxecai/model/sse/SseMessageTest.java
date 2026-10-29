package com.thinkxecai.model.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.controller.ChatMessageController;
import com.thinkxecai.model.ChatMessageMetadata;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.ToolCallMetadata;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.ChatMessageFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The stream's JSON is what the frontend's hand-written {@code SseMessageEvent} in ui/src/api/api.ts
 * expects (ADR 021), and a pushed message is the same JSON as the REST history returns for it.
 */
@WebMvcTest(ChatMessageController.class)
class SseMessageTest {

    private static final Path FRONTEND_API = Path.of("..", "ui", "src", "api", "api.ts");
    private static final UUID ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-a2b3-c4d5e6f7a8b9");
    private static final UUID SESSION = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 8, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @MockitoBean
    private ChatMessageFacadeService service;

    /** The ObjectMapper Spring MVC writes response bodies with, as SseEmitter's JSON events will be. */
    private ObjectMapper mvcObjectMapper() {
        return handlerAdapter.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(converter -> ((MappingJackson2HttpMessageConverter) converter).getObjectMapper())
                .findFirst().orElseThrow();
    }

    private static ChatMessageVO toolCall() {
        return new ChatMessageVO(ID.toString(), SESSION.toString(), ChatMessageRole.ASSISTANT, "",
                new ChatMessageMetadata(List.of(new ToolCallMetadata("call_1", "function", "getDate", "{}")), null),
                CREATED, CREATED);
    }

    private static List<String> keys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    void aGeneratedMessageHasExactlyTheFrontendsFields() {
        JsonNode json = mvcObjectMapper().valueToTree(SseMessage.generated(toolCall()));

        assertThat(keys(json)).containsExactly("type", "payload", "metadata");
        assertThat(json.get("type").asText()).isEqualTo("AI_GENERATED_CONTENT");
        assertThat(keys(json.get("payload"))).containsExactly("message", "statusText", "done");
        // present as null, not left out: the frontend's type is string | null
        assertThat(json.get("payload").get("statusText").isNull()).isTrue();
        assertThat(json.get("payload").get("done").isBoolean()).isTrue();
        assertThat(json.get("payload").get("done").asBoolean()).isFalse();
        assertThat(keys(json.get("metadata"))).containsExactly("chatMessageId");
        assertThat(json.get("metadata").get("chatMessageId").asText()).isEqualTo(ID.toString());
    }

    @Test
    void thePushedMessageIsTheJsonTheHistoryReturns() throws Exception {
        ChatMessageVO message = toolCall();
        when(service.listChatMessagesBySession(SESSION)).thenReturn(List.of(message));
        ObjectMapper mapper = mvcObjectMapper();

        String history = mockMvc.perform(get("/api/chat-messages/session/" + SESSION))
                .andReturn().getResponse().getContentAsString();

        assertThat(mapper.valueToTree(SseMessage.generated(message)).get("payload").get("message"))
                .isEqualTo(mapper.readTree(history).get("data").get(0));
    }

    @Test
    void theTypesAreTheFrontendsSseMessageTypes() throws Exception {
        String api = Files.readString(FRONTEND_API);
        Matcher union = Pattern.compile("export type SseMessageType =((?:\\s*\\|\\s*'\\w+')+)").matcher(api);
        assertThat(union.find()).as("SseMessageType in %s", FRONTEND_API).isTrue();
        List<String> frontend = Pattern.compile("'(\\w+)'").matcher(union.group(1)).results()
                .map(match -> match.group(1)).toList();

        assertThat(Arrays.stream(SseMessage.Type.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(frontend);
    }

}
