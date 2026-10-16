package com.thinkxecai.service.impl;

import com.thinkxecai.converter.ChatMessageConverter;
import com.thinkxecai.event.ChatEvent;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.ChatMessageMapper;
import com.thinkxecai.mapper.ChatSessionMapper;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.entity.ChatMessageEntity;
import com.thinkxecai.model.entity.ChatSessionEntity;
import com.thinkxecai.model.request.CreateChatMessageRequest;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.ChatMessageFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageFacadeServiceImpl implements ChatMessageFacadeService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageConverter chatMessageConverter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<ChatMessageVO> listChatMessagesBySession(UUID sessionId) {
        findSession(sessionId);
        return chatMessageMapper.listBySessionId(sessionId).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public ChatMessageVO createChatMessage(CreateChatMessageRequest request) {
        ChatSessionEntity session = findSession(request.getSessionId());
        if (session.getAgentId() == null) {
            throw new BizException(409, "Chat session has no agent");
        }

        ChatMessageDTO dto = chatMessageConverter.fromCreateRequest(request);
        ChatMessageEntity entity = chatMessageConverter.toEntity(dto);
        chatMessageMapper.insert(entity);
        chatSessionMapper.touch(session.getId());
        eventPublisher.publishEvent(new ChatEvent(session.getAgentId(), session.getId()));
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public void deleteChatMessage(UUID chatMessageId) {
        if (chatMessageMapper.deleteById(chatMessageId) == 0) {
            throw notFound();
        }
    }

    private ChatSessionEntity findSession(UUID sessionId) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(404, "Chat session not found");
        }
        return session;
    }

    private ChatMessageEntity findExisting(UUID chatMessageId) {
        ChatMessageEntity entity = chatMessageMapper.selectById(chatMessageId);
        if (entity == null) {
            throw notFound();
        }
        return entity;
    }

    private ChatMessageVO toVO(ChatMessageEntity entity) {
        return chatMessageConverter.toVO(chatMessageConverter.toDTO(entity));
    }

    private static BizException notFound() {
        return new BizException(404, "Chat message not found");
    }

}
