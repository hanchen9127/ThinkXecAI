package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateKnowledgeBaseRequest;
import com.thinkxecai.model.request.UpdateKnowledgeBaseRequest;
import com.thinkxecai.model.vo.KnowledgeBaseVO;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseFacadeService {

    /** Most recently updated first. */
    List<KnowledgeBaseVO> listKnowledgeBases();

    KnowledgeBaseVO createKnowledgeBase(CreateKnowledgeBaseRequest request);

    /** PATCH semantics. Unknown id → BizException 404. */
    KnowledgeBaseVO updateKnowledgeBase(UUID knowledgeBaseId, UpdateKnowledgeBaseRequest request);

    /**
     * Deletes the knowledge base with its documents and chunks (foreign key cascade) and removes it
     * from every agent's allowedKbs; its stored files go after the commit. Unknown id → BizException 404.
     */
    void deleteKnowledgeBase(UUID knowledgeBaseId);

}
