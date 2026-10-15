package com.thinkxecai.service;

import com.thinkxecai.model.vo.ToolVO;

import java.util.List;

/** The tools agents can use: fixed tools go to every agent, optional ones only where listed in {@code allowedTools}. */
public interface ToolFacadeService {

    /** Fixed tools first, then optional ones, each group sorted by name. */
    List<ToolVO> listTools();

    List<ToolVO> listFixedTools();

    /** The tools an agent can choose from; what {@code GET /api/tools} returns. */
    List<ToolVO> listOptionalTools();

}
