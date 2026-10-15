package com.thinkxecai.model.vo;

import com.thinkxecai.agent.tool.ToolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A tool as shown to people configuring an agent; {@code name} is what {@code allowedTools} stores. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolVO {

    private String name;
    private String description;
    private ToolType type;

}
