package com.thinkxecai.service.impl;

import com.thinkxecai.converter.DocumentConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.ChunkMapper;
import com.thinkxecai.mapper.DocumentMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.MarkdownSection;
import com.thinkxecai.model.dto.DocumentDTO;
import com.thinkxecai.model.entity.ChunkEntity;
import com.thinkxecai.model.entity.DocumentEntity;
import com.thinkxecai.model.request.CreateDocumentRequest;
import com.thinkxecai.model.request.UpdateDocumentRequest;
import com.thinkxecai.model.vo.DocumentVO;
import com.thinkxecai.service.DocumentFacadeService;
import com.thinkxecai.service.DocumentStorageService;
import com.thinkxecai.service.MarkdownParserService;
import com.thinkxecai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentFacadeServiceImpl implements DocumentFacadeService {

    /** The stored file's extension always comes from this list, never from the client's file name. */
    private static final Set<String> MARKDOWN_EXTENSIONS = Set.of("md", "markdown");

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkMapper chunkMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final RagService ragService;
    private final TransactionTemplate transactionTemplate;

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

    /**
     * Everything that can fail on bad input, and the slow embedding call, happens before the
     * transaction; the transaction then only writes (ADR 025).
     */
    @Override
    public DocumentVO uploadDocument(UUID kbId, String fileName, byte[] content) {
        if (knowledgeBaseMapper.selectById(kbId) == null) {
            throw new BizException(400, "kbId: unknown knowledge base " + kbId);
        }
        String name = baseName(fileName);
        String extension = markdownExtension(name);
        if (content.length == 0) {
            throw new BizException(400, "file: is empty");
        }
        List<MarkdownSection> sections = markdownParserService.parse(decodeUtf8(content));
        if (sections.isEmpty()) {
            throw new BizException(400, "file: has no heading with text; at least one is needed to create searchable sections");
        }
        List<float[]> embeddings = ragService.embed(sections.stream().map(MarkdownSection::title).toList());
        if (embeddings.size() != sections.size()) {
            throw new IllegalStateException("Expected " + sections.size() + " embeddings but got " + embeddings.size());
        }

        DocumentEntity stored = transactionTemplate.execute(status -> {
            DocumentEntity document = DocumentEntity.builder().kbId(kbId).name(name).build();
            documentMapper.insert(document);
            UUID documentId = document.getId();
            // Registered before the file is written, so a rollback also removes a partly written directory.
            FileCleanup.unlessCommitted("failed upload " + documentId,
                    () -> documentStorageService.deleteDocumentFiles(kbId, documentId));

            String filePath = documentStorageService.save(kbId, documentId, extension, content);
            DocumentDTO withMetadata = DocumentDTO.builder().id(documentId).metadata(Map.of("filePath", filePath)).build();
            documentMapper.update(documentConverter.toEntity(withMetadata));

            for (int i = 0; i < sections.size(); i++) {
                chunkMapper.insert(ChunkEntity.builder()
                        .kbId(kbId).docId(documentId)
                        .title(sections.get(i).title()).content(sections.get(i).content())
                        .embedding(embeddings.get(i))
                        .build());
            }
            return findExisting(documentId);
        });
        return toVO(stored);
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
        UUID kbId = findExisting(documentId).getKbId();
        documentMapper.deleteById(documentId);
        FileCleanup.afterCommit("document " + documentId, () -> documentStorageService.deleteDocumentFiles(kbId, documentId));
    }

    /** Browsers send a bare file name, but some clients include a path; only the last segment is kept. */
    private static String baseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).strip();
    }

    private static String markdownExtension(String name) {
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!MARKDOWN_EXTENSIONS.contains(extension)) {
            throw new BizException(400, "file: only Markdown files (.md, .markdown) are accepted");
        }
        return extension;
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new BizException(400, "file: must be UTF-8 encoded text");
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
