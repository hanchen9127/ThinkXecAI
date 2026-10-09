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
import com.thinkxecai.service.AgentFacadeService;
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

    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.listAll().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public AgentVO createAgent(CreateAgentRequest request) {
        requireKnowledgeBasesExist(request.getAllowedKbs());
        AgentDTO dto = agentConverter.fromCreateRequest(request);
        AgentEntity entity = agentConverter.toEntity(dto);
        agentMapper.insert(entity);
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public AgentVO updateAgent(UUID agentId, UpdateAgentRequest request) {
        AgentDTO existing = agentConverter.toDTO(findExisting(agentId));
        requireKnowledgeBasesExist(request.getAllowedKbs());
        AgentDTO updated = agentConverter.applyUpdateRequest(existing, request);
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

    /** allowedKbs has no foreign key (JSONB array), so references are checked here before writing. */
    private void requireKnowledgeBasesExist(List<UUID> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return;
        }
        Set<UUID> found = knowledgeBaseMapper.selectByIds(kbIds).stream()
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
