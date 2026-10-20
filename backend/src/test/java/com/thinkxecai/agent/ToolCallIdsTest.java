package com.thinkxecai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every stored tool call has an id, and filling one keeps what Gemini needs back (its signatures). */
class ToolCallIdsTest {

    private static AssistantMessage.ToolCall call(String id, String name) {
        return new AssistantMessage.ToolCall(id, "function", name, "{}");
    }

    @Test
    void blankIdsAreFilledAndExistingOnesKept() {
        AssistantMessage message = AssistantMessage.builder()
                .content("Checking.")
                .toolCalls(List.of(call("", "getCity"), call("call_7", "getDate"), call(null, "weather")))
                .build();

        AssistantMessage filled = ToolCallIds.fill(message);

        assertThat(filled.getToolCalls()).extracting(AssistantMessage.ToolCall::id)
                .satisfies(ids -> {
                    assertThat(ids.get(0)).matches("call_[0-9a-f-]{36}");
                    assertThat(ids.get(1)).isEqualTo("call_7");
                    assertThat(ids.get(2)).matches("call_[0-9a-f-]{36}").isNotEqualTo(ids.get(0));
                });
        assertThat(filled.getToolCalls()).extracting(AssistantMessage.ToolCall::name)
                .containsExactly("getCity", "getDate", "weather");
        assertThat(filled.getText()).isEqualTo("Checking.");
    }

    @Test
    void thoughtSignaturesAndOtherMetadataSurviveTheFill() {
        List<byte[]> signatures = List.of("signature".getBytes(StandardCharsets.UTF_8));
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .properties(Map.of("thoughtSignatures", signatures, "finishReason", "STOP"))
                .toolCalls(List.of(call("", "getDate")))
                .build();

        AssistantMessage filled = ToolCallIds.fill(message);

        assertThat(filled.getMetadata()).containsEntry("thoughtSignatures", signatures).containsEntry("finishReason", "STOP");
    }

    @Test
    void aMessageWhoseCallsAllHaveIdsIsReturnedAsIs() {
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of(call("a", "getDate"))).build();

        assertThat(ToolCallIds.fill(message)).isSameAs(message);
    }

    @Test
    void storingACallWithoutAnIdIsRefused() {
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of(call("", "getDate"))).build();

        assertThatThrownBy(() -> GeneratedMessages.assistant(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getDate");
    }

}
