package com.thinkxecai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ModelOptionsUtils;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Tool data that Spring AI's Gemini model can parse; it reads both with this same mapper. */
class ToolJsonTest {

    @Test
    void aJsonResultIsKept() {
        assertThat(ToolJson.result("{\"sky\":\"sunny\"}")).isEqualTo("{\"sky\":\"sunny\"}");
        assertThat(ToolJson.result("\"2026-10-20\"")).isEqualTo("\"2026-10-20\"");
        assertThat(ToolJson.result("[1,2]")).isEqualTo("[1,2]");
    }

    @Test
    void anythingElseBecomesAJsonString() {
        assertThat(ToolJson.result("Unknown tool: x.")).isEqualTo("\"Unknown tool: x.\"");
        // one value followed by more text is not one JSON value
        assertThat(ToolJson.result("2026-10-20")).isEqualTo("\"2026-10-20\"");
        assertThat(ToolJson.result("say \"hi\"\n")).isEqualTo("\"say \\\"hi\\\"\\n\"");
        assertThat(ToolJson.result("")).isEqualTo("\"\"");
        assertThat(ToolJson.result(null)).isEqualTo("\"\"");
    }

    @Test
    void argumentsThatAreNotJsonBecomeAnEmptyObject() {
        assertThat(ToolJson.arguments("{\"city\":\"Sydney\"}")).isEqualTo("{\"city\":\"Sydney\"}");
        assertThat(ToolJson.arguments("{\"city\": Sydney")).isEqualTo("{}");
        assertThat(ToolJson.arguments(" ")).isEqualTo("{}");
        assertThat(ToolJson.arguments(null)).isEqualTo("{}");
    }

    @Test
    void geminiCanParseEveryOutput() {
        Stream.of(ToolJson.result("Unknown tool: x."), ToolJson.result(""), ToolJson.result("2026-10-20"),
                        ToolJson.arguments("{\"city\": Sydney"), ToolJson.arguments(""))
                .forEach(json -> assertThatCode(() -> ModelOptionsUtils.OBJECT_MAPPER.readValue(json, Object.class))
                        .doesNotThrowAnyException());
    }

}
