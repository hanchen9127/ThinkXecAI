package com.thinkxecai.service.impl;

import com.thinkxecai.converter.ChatMessageConverter;
import com.thinkxecai.exception.ChatSessionGoneException;
import com.thinkxecai.mapper.ChatMessageMapper;
import com.thinkxecai.mapper.ChatSessionMapper;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.entity.ChatMessageEntity;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.GeneratedMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeneratedMessageServiceImpl implements GeneratedMessageService {

    private static final Set<ChatMessageRole> GENERATED_ROLES = Set.of(ChatMessageRole.ASSISTANT, ChatMessageRole.TOOL);

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageConverter chatMessageConverter;

    @Override
    @Transactional
    public List<ChatMessageVO> save(UUID sessionId, List<ChatMessageDTO> messages) {
        for (ChatMessageDTO message : messages) {
            if (!GENERATED_ROLES.contains(message.getRole())) {
                throw new IllegalArgumentException("Not a generated message role: " + message.getRole());
            }
        }
        // The same lock as a new user message (ADR 037): the session cannot be deleted while its messages
        // are saved, and a user message sent meanwhile waits instead of landing between them.
        if (chatSessionMapper.selectByIdForNoKeyUpdate(sessionId) == null) {
            throw new ChatSessionGoneException(sessionId);
        }
        List<ChatMessageVO> saved = new ArrayList<>(messages.size());
        for (ChatMessageDTO message : messages) {
            ChatMessageEntity entity = chatMessageConverter.toEntity(message);
            entity.setSessionId(sessionId);
            chatMessageMapper.insert(entity);
            saved.add(chatMessageConverter.toVO(chatMessageConverter.toDTO(chatMessageMapper.selectById(entity.getId()))));
        }
        chatSessionMapper.touch(sessionId);
        return saved;
    }

}
