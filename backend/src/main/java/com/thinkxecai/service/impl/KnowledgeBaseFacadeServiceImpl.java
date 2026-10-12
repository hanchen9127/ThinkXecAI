package com.thinkxecai.service.impl;

import com.thinkxecai.converter.KnowledgeBaseConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import com.thinkxecai.model.entity.KnowledgeBaseEntity;
import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;
import com.thinkxecai.service.DocumentStorageService;
import com.thinkxecai.service.KnowledgeBaseFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseFacadeServiceImpl implements KnowledgeBaseFacadeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentMapper agentMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final DocumentStorageService documentStorageService;

    @Override
    public List<KnowledgeBaseVO> listKnowledgeBases() {
        return knowledgeBaseMapper.listAll().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public KnowledgeBaseVO createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        KnowledgeBaseDTO dto = knowledgeBaseConverter.fromCreateRequest(request);
        KnowledgeBaseEntity entity = knowledgeBaseConverter.toEntity(dto);
        knowledgeBaseMapper.insert(entity);
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public KnowledgeBaseVO updateKnowledgeBase(UUID knowledgeBaseId, UpdateKnowledgeBaseRequest request) {
        KnowledgeBaseDTO existing = knowledgeBaseConverter.toDTO(findExisting(knowledgeBaseId));
        KnowledgeBaseDTO updated = knowledgeBaseConverter.applyUpdateRequest(existing, request);
        knowledgeBaseMapper.update(knowledgeBaseConverter.toEntity(updated));
        return toVO(findExisting(knowledgeBaseId));
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(UUID knowledgeBaseId) {
        if (knowledgeBaseMapper.deleteById(knowledgeBaseId) == 0) {
            throw notFound();
        }
        agentMapper.removeAllowedKb(knowledgeBaseId);
        FileCleanup.afterCommit("knowledge base " + knowledgeBaseId,
                () -> documentStorageService.deleteKnowledgeBaseFiles(knowledgeBaseId));
    }

    private KnowledgeBaseEntity findExisting(UUID knowledgeBaseId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (entity == null) {
            throw notFound();
        }
        return entity;
    }

    private KnowledgeBaseVO toVO(KnowledgeBaseEntity entity) {
        return knowledgeBaseConverter.toVO(knowledgeBaseConverter.toDTO(entity));
    }

    private static BizException notFound() {
        return new BizException(404, "Knowledge base not found");
    }

}
