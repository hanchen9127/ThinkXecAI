package com.thinkxecai.agent.tool;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class DateTool implements Tool {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Clock clock;

    public DateTool() {
        this(Clock.systemDefaultZone());
    }

    DateTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "dateTool";
    }

    @Override
    public String getDescription() {
        return "Returns today's date.";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getDate", description = "Get today's date, formatted as yyyy-MM-dd.")
    public String getDate() {
        return LocalDate.now(clock).format(FORMAT);
    }

}
