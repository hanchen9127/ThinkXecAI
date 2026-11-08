package com.thinkxecai.model.vo;

import com.thinkxecai.agent.tool.ToolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tool as people see it (ADR 065). {@code name} is what {@code allowedTools} stores,
 * {@code callName} what the model calls and what a tool call's metadata names, and {@code label}
 * what the UI shows for either.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolVO {

    private String name;
    private String callName;
    private String label;
    private String description;
    private ToolType type;

}
