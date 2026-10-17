package com.thinkxecai.service.impl;

import com.thinkxecai.converter.ChatSessionConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.ChatSessionMapper;
import com.thinkxecai.model.dto.ChatSessionDTO;
import com.thinkxecai.model.entity.ChatSessionEntity;
import com.thinkxecai.model.request.CreateChatSessionRequest;
import com.thinkxecai.model.request.UpdateChatSessionRequest;
import com.thinkxecai.model.vo.ChatSessionVO;
import com.thinkxecai.service.ChatSessionFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionFacadeServiceImpl implements ChatSessionFacadeService {

    private final ChatSessionMapper chatSessionMapper;
    private final AgentMapper agentMapper;
    private final ChatSessionConverter chatSessionConverter;

    @Override
    public List<ChatSessionVO> listChatSessions() {
        return toVOs(chatSessionMapper.listAll());
    }

    @Override
    public ChatSessionVO getChatSession(UUID chatSessionId) {
        return toVO(findExisting(chatSessionId));
    }

    @Override
    public List<ChatSessionVO> listChatSessionsByAgent(UUID agentId) {
        if (agentMapper.selectById(agentId) == null) {
            throw new BizException(404, "Agent not found");
        }
        return toVOs(chatSessionMapper.listByAgentId(agentId));
    }

    @Override
    @Transactional
    public ChatSessionVO createChatSession(CreateChatSessionRequest request) {
        // FOR SHARE: the agent cannot be deleted before this session is saved (ADR 037).
        if (request.getAgentId() != null && agentMapper.selectByIdForShare(request.getAgentId()) == null) {
            throw new BizException(400, "agentId: unknown agent " + request.getAgentId());
        }
        ChatSessionDTO dto = chatSessionConverter.fromCreateRequest(request);
        ChatSessionEntity entity = chatSessionConverter.toEntity(dto);
        chatSessionMapper.insert(entity);
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public ChatSessionVO updateChatSession(UUID chatSessionId, UpdateChatSessionRequest request) {
        ChatSessionDTO existing = chatSessionConverter.toDTO(findExisting(chatSessionId));
        ChatSessionEntity changes = chatSessionConverter.toEntity(chatSessionConverter.applyUpdateRequest(existing, request));
        // Never write agent_id here: it is fixed, and the agent may have been deleted meanwhile.
        changes.setAgentId(null);
        chatSessionMapper.update(changes);
        return toVO(findExisting(chatSessionId));
    }

    @Override
    @Transactional
    public void deleteChatSession(UUID chatSessionId) {
        if (chatSessionMapper.deleteById(chatSessionId) == 0) {
            throw notFound();
        }
    }

    private ChatSessionEntity findExisting(UUID chatSessionId) {
        ChatSessionEntity entity = chatSessionMapper.selectById(chatSessionId);
        if (entity == null) {
            throw notFound();
        }
        return entity;
    }

    private List<ChatSessionVO> toVOs(List<ChatSessionEntity> entities) {
        return entities.stream().map(this::toVO).toList();
    }

    private ChatSessionVO toVO(ChatSessionEntity entity) {
        return chatSessionConverter.toVO(chatSessionConverter.toDTO(entity));
    }

    private static BizException notFound() {
        return new BizException(404, "Chat session not found");
    }

}
