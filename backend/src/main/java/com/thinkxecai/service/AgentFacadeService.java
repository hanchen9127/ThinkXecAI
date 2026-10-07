package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;

import java.util.List;
import java.util.UUID;

public interface AgentFacadeService {

    /** Most recently created first. */
    List<AgentVO> listAgents();

    /** Unknown knowledge base ids in allowedKbs → BizException 400. */
    AgentVO createAgent(CreateAgentRequest request);

    /** PATCH semantics. Unknown id → BizException 404; unknown knowledge base ids → 400. */
    AgentVO updateAgent(UUID agentId, UpdateAgentRequest request);

    /** The agent's chat sessions are kept without an agent. Unknown id → BizException 404. */
    void deleteAgent(UUID agentId);

}
