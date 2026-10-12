package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool names agents store and the method names the model sees are part of the spec, so they are
 * read here through the same Spring AI conversion the runtime uses.
 */
class FixedToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<Arguments> fixedTools() {
        return Stream.of(
                Arguments.of(new TerminateTool(), "terminate", "terminate"),
                Arguments.of(new CityTool(), "cityTool", "getCity"),
                Arguments.of(new DateTool(), "dateTool", "getDate"),
                Arguments.of(new WeatherTool(), "weatherTool", "weather"));
    }

    @ParameterizedTest
    @MethodSource("fixedTools")
    void eachFixedToolHasItsSpecifiedNameAndExposesOneMethodToTheModel(Tool tool, String name, String methodName) {
        assertThat(tool.getName()).isEqualTo(name);
        assertThat(tool.getType()).isEqualTo(ToolType.FIXED);
        assertThat(tool.getDescription()).isNotBlank();

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks();
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo(methodName);
        assertThat(callbacks[0].getToolDefinition().description()).isNotBlank();
    }

    @Test
    void getCityReturnsTheFixedTestCityAndSaysItIsNotARealLocation() {
        assertThat(new CityTool().getCity()).isEqualTo("Sydney (sample value, not the user's real location)");
    }

    @Test
    void getDateFormatsTodayAsIsoDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-04T10:15:30Z"), ZoneId.of("UTC"));

        assertThat(new DateTool(clock).getDate()).isEqualTo("2026-03-04");
    }

    @Test
    void weatherTakesCityAndDateAndSaysItIsSampleData() throws Exception {
        ToolDefinition definition = MethodToolCallbackProvider.builder().toolObjects(new WeatherTool()).build()
                .getToolCallbacks()[0].getToolDefinition();
        JsonNode schema = JSON.readTree(definition.inputSchema());

        assertThat(schema.get("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("city", "date");
        assertThat(new WeatherTool().getWeather("Sydney", "2026-03-04"))
                .contains("Sydney", "2026-03-04", "sample data");
    }

}
