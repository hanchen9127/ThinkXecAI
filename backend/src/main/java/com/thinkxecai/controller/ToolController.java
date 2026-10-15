package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.ToolFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolFacadeService toolFacadeService;

    /** Only optional tools: fixed tools are given to every agent, so there is nothing to choose. */
    @GetMapping
    public ApiResponse<List<ToolVO>> listOptionalTools() {
        return ApiResponse.success(toolFacadeService.listOptionalTools());
    }

}
