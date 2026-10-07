package com.thinkxecai.controller;

import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;
import com.thinkxecai.service.AgentFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentFacadeService agentFacadeService;

    @GetMapping
    public ApiResponse<List<AgentVO>> listAgents() {
        return ApiResponse.success(agentFacadeService.listAgents());
    }

    @PostMapping
    public ApiResponse<AgentVO> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentFacadeService.createAgent(request));
    }

    @PatchMapping("/{agentId}")
    public ApiResponse<AgentVO> updateAgent(@PathVariable UUID agentId, @Valid @RequestBody UpdateAgentRequest request) {
        return ApiResponse.success(agentFacadeService.updateAgent(agentId, request));
    }

    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> deleteAgent(@PathVariable UUID agentId) {
        agentFacadeService.deleteAgent(agentId);
        return ApiResponse.success();
    }

}
