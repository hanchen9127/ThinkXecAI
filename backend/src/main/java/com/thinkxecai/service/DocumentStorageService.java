package com.thinkxecai.service;

import java.util.UUID;

/**
 * Stores uploaded files under {@code thinkxecai.document.storage-path} as
 * {@code {kbId}/{documentId}/{uuid}.{extension}}. Every path is built from ids, never read back from
 * document metadata, and must stay inside the storage directory (ADR 019).
 */
public interface DocumentStorageService {

    /**
     * Writes the file and returns its path relative to the storage directory, with {@code /} separators.
     * The extension must be lowercase letters and digits only; anything else → IllegalArgumentException.
     */
    String save(UUID kbId, UUID documentId, String extension, byte[] content);

    /** Deletes the document's directory and everything in it. A missing directory is a no-op. */
    void deleteDocumentFiles(UUID kbId, UUID documentId);

    /** Deletes the knowledge base's directory and everything in it. A missing directory is a no-op. */
    void deleteKnowledgeBaseFiles(UUID kbId);

}
