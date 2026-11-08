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

    /**
     * Every tool people are shown, fixed and optional, so the UI can name a tool by its label wherever
     * it appears (ADR 065); the agent form offers only the optional ones.
     */
    @GetMapping
    public ApiResponse<List<ToolVO>> listTools() {
        return ApiResponse.success(toolFacadeService.listTools());
    }

}
