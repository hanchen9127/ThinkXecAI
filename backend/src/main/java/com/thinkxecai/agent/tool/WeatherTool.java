package com.thinkxecai.agent.tool;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Test tool with a canned answer, for trying out tool calls with arguments. The answer says it is
 * sample data, so the model does not pass it off as a real forecast.
 */
@Component
public class WeatherTool implements Tool {

    @Override
    public String getName() {
        return "weatherTool";
    }

    @Override
    public String getLabel() {
        return "Sample weather";
    }

    @Override
    public String getDescription() {
        return "Returns sample weather for a city and date (test tool, no real forecast).";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "weather", description = "Get the weather for a city on a date.")
    public String getWeather(@ToolParam(description = "City name") String city,
                             @ToolParam(description = "Date as yyyy-MM-dd") String date) {
        return "Weather in " + city + " on " + date + ": sunny to cloudy, 25°C, humidity 60% (sample data, not a live forecast).";
    }

}
