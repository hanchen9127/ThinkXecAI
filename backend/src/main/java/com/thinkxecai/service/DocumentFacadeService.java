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

    /** Creates the record only, without a file. Unknown knowledge base → BizException 400. */
    DocumentVO createDocument(CreateDocumentRequest request);

    /**
     * Stores one Markdown file and ingests it into chunks, all or nothing (ADR 025). Checked before
     * any work, each → BizException 400: knowledge base exists; file name ends in .md / .markdown
     * (any case); content not empty; valid UTF-8; at least one heading with text. The section titles
     * are embedded before the transaction; a failure there or later leaves no row, chunk, or file.
     * Takes plain bytes, not a multipart file, so a future batch upload can run each file through it.
     */
    DocumentVO uploadDocument(UUID kbId, String fileName, byte[] content);

    /** Changes the name only. Unknown id → BizException 404. */
    DocumentVO updateDocument(UUID documentId, UpdateDocumentRequest request);

    /** Deletes the document and its chunks (foreign key cascade); its stored files go after the commit. Unknown id → BizException 404. */
    void deleteDocument(UUID documentId);

}
