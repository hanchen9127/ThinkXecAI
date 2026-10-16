package com.thinkxecai.service.impl;

import com.thinkxecai.converter.AgentConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.entity.AgentEntity;
import com.thinkxecai.model.entity.KnowledgeBaseEntity;
import com.thinkxecai.model.request.CreateAgentRequest;
import com.thinkxecai.model.request.UpdateAgentRequest;
import com.thinkxecai.model.vo.AgentVO;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.AgentFacadeService;
import com.thinkxecai.service.ToolFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentFacadeServiceImpl implements AgentFacadeService {

    private final AgentMapper agentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentConverter agentConverter;
    private final ToolFacadeService toolFacadeService;

    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.listAll().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public AgentVO createAgent(CreateAgentRequest request) {
        requireOptionalTools(request.getAllowedTools());
        requireKnowledgeBasesExist(request.getAllowedKbs());
        AgentDTO dto = agentConverter.fromCreateRequest(request);
        AgentEntity entity = agentConverter.toEntity(dto);
        agentMapper.insert(entity);
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public AgentVO updateAgent(UUID agentId, UpdateAgentRequest request) {
        findExisting(agentId);
        requireOptionalTools(request.getAllowedTools());
        // Knowledge bases are locked before the agent, the same order as a knowledge-base delete
        // (its row, then the agents), so the two cannot deadlock (ADR 037).
        requireKnowledgeBasesExist(request.getAllowedKbs());
        AgentEntity locked = agentMapper.selectByIdForUpdate(agentId);
        if (locked == null) {
            throw notFound();
        }
        AgentDTO updated = agentConverter.applyUpdateRequest(agentConverter.toDTO(locked), request);
        agentMapper.update(agentConverter.toEntity(updated));
        return toVO(findExisting(agentId));
    }

    @Override
    @Transactional
    public void deleteAgent(UUID agentId) {
        if (agentMapper.deleteById(agentId) == 0) {
            throw notFound();
        }
    }

    /** Only optional tools can be chosen; fixed tools are given to every agent and are not listed here. */
    private void requireOptionalTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return;
        }
        Set<String> optional = toolFacadeService.listOptionalTools().stream()
                .map(ToolVO::getName)
                .collect(Collectors.toSet());
        List<String> unknown = toolNames.stream()
                .filter(name -> !optional.contains(name))
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BizException(400, "allowedTools: not an optional tool: " + String.join(", ", unknown));
        }
    }

    /** allowedKbs has no foreign key (JSONB array), so references are checked here before writing. */
    private void requireKnowledgeBasesExist(List<UUID> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return;
        }
        // FOR SHARE: a knowledge base found here cannot be deleted before this agent is saved (ADR 037).
        Set<UUID> found = knowledgeBaseMapper.selectByIdsForShare(kbIds.toArray(UUID[]::new)).stream()
                .map(KnowledgeBaseEntity::getId)
                .collect(Collectors.toSet());
        List<String> unknown = kbIds.stream()
                .filter(id -> !found.contains(id))
                .distinct()
                .map(UUID::toString)
                .toList();
        if (!unknown.isEmpty()) {
            throw new BizException(400, "allowedKbs: unknown knowledge base " + String.join(", ", unknown));
        }
    }

    private AgentEntity findExisting(UUID agentId) {
        AgentEntity entity = agentMapper.selectById(agentId);
        if (entity == null) {
            throw notFound();
        }
        return entity;
    }

    private AgentVO toVO(AgentEntity entity) {
        return agentConverter.toVO(agentConverter.toDTO(entity));
    }

    private static BizException notFound() {
        return new BizException(404, "Agent not found");
    }

}
