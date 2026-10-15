package com.thinkxecai.service;

import com.thinkxecai.model.vo.ToolVO;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/** The tools agents can use: fixed tools go to every agent, optional ones only where listed in {@code allowedTools}. */
public interface ToolFacadeService {

    /** Fixed tools first, then optional ones, each group sorted by name. */
    List<ToolVO> listTools();

    List<ToolVO> listFixedTools();

    /** The tools an agent can choose from; what {@code GET /api/tools} returns. */
    List<ToolVO> listOptionalTools();

    /**
     * The Spring AI callbacks for one agent run: every fixed tool, plus each optional tool named in
     * {@code allowedTools}. Names that are not optional tools (stored before validation existed, or a
     * fixed tool) are skipped with a warning, so an old agent still runs.
     */
    List<ToolCallback> toolCallbacksFor(List<String> allowedTools);

}
