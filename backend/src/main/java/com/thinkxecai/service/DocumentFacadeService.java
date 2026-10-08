package com.thinkxecai.service;

import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;

import java.util.List;
import java.util.UUID;

public interface DocumentFacadeService {

    /** Most recently updated first. */
    List<DocumentVO> listDocuments();

    /** Most recently updated first. Unknown knowledge base → BizException 404. */
    List<DocumentVO> listDocumentsByKb(UUID kbId);

    /** Creates the record only (no file; uploads are Phase 8). Unknown knowledge base → BizException 400. */
    DocumentVO createDocument(CreateDocumentRequest request);

    /** Changes the name only. Unknown id → BizException 404. */
    DocumentVO updateDocument(UUID documentId, UpdateDocumentRequest request);

    /** Deletes the document and its chunks (foreign key cascade). Unknown id → BizException 404. */
    void deleteDocument(UUID documentId);

}
