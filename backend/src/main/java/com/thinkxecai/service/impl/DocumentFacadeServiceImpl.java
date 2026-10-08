package com.thinkxecai.service.impl;

import com.thinkxecai.converter.DocumentConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.DocumentMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.dto.DocumentDTO;
import com.thinkxecai.model.entity.DocumentEntity;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import com.thinkxecai.service.DocumentFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentFacadeServiceImpl implements DocumentFacadeService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentConverter documentConverter;

    @Override
    public List<DocumentVO> listDocuments() {
        return toVOs(documentMapper.listAll());
    }

    @Override
    public List<DocumentVO> listDocumentsByKb(UUID kbId) {
        if (knowledgeBaseMapper.selectById(kbId) == null) {
            throw new BizException(404, "Knowledge base not found");
        }
        return toVOs(documentMapper.listByKbId(kbId));
    }

    @Override
    @Transactional
    public DocumentVO createDocument(CreateDocumentRequest request) {
        if (knowledgeBaseMapper.selectById(request.getKbId()) == null) {
            throw new BizException(400, "kbId: unknown knowledge base " + request.getKbId());
        }
        DocumentDTO dto = documentConverter.fromCreateRequest(request);
        DocumentEntity entity = documentConverter.toEntity(dto);
        documentMapper.insert(entity);
        return toVO(findExisting(entity.getId()));
    }

    @Override
    @Transactional
    public DocumentVO updateDocument(UUID documentId, UpdateDocumentRequest request) {
        DocumentDTO existing = documentConverter.toDTO(findExisting(documentId));
        DocumentDTO updated = documentConverter.applyUpdateRequest(existing, request);
        documentMapper.update(documentConverter.toEntity(updated));
        return toVO(findExisting(documentId));
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId) {
        if (documentMapper.deleteById(documentId) == 0) {
            throw notFound();
        }
    }

    private DocumentEntity findExisting(UUID documentId) {
        DocumentEntity entity = documentMapper.selectById(documentId);
        if (entity == null) {
            throw notFound();
        }
        return entity;
    }

    private List<DocumentVO> toVOs(List<DocumentEntity> entities) {
        return entities.stream().map(this::toVO).toList();
    }

    private DocumentVO toVO(DocumentEntity entity) {
        return documentConverter.toVO(documentConverter.toDTO(entity));
    }

    private static BizException notFound() {
        return new BizException(404, "Document not found");
    }

}
