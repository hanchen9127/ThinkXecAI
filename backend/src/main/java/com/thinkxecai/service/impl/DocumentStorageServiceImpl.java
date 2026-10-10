package com.thinkxecai.service.impl;

import com.thinkxecai.service.DocumentStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Directories are built only from UUIDs (hex digits and dashes), so they cannot leave the storage
 * directory; the extension is the one caller-supplied part of a path and is checked twice.
 */
@Service
public class DocumentStorageServiceImpl implements DocumentStorageService {

    /** Rules out separators, "..", NTFS stream names ("md:x") and empty extensions before any path is built. */
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,16}");

    private final Path root;

    public DocumentStorageServiceImpl(@Value("${thinkxecai.document.storage-path}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public String save(UUID kbId, UUID documentId, String extension, byte[] content) {
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid file extension: " + extension);
        }
        Path documentDirectory = documentDirectory(kbId, documentId);
        Path file = documentDirectory.resolve(UUID.randomUUID() + "." + extension).normalize();
        // Second layer: whatever the extension check lets through must still land directly in the document's directory.
        if (!documentDirectory.equals(file.getParent())) {
            throw new IllegalArgumentException("Refusing to write outside the document directory: " + file);
        }
        try {
            Files.createDirectories(documentDirectory);
            Files.write(file, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store file " + file, e);
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    @Override
    public void deleteDocumentFiles(UUID kbId, UUID documentId) {
        deleteRecursively(documentDirectory(kbId, documentId));
    }

    @Override
    public void deleteKnowledgeBaseFiles(UUID kbId) {
        deleteRecursively(root.resolve(kbId.toString()));
    }

    private Path documentDirectory(UUID kbId, UUID documentId) {
        return root.resolve(kbId.toString()).resolve(documentId.toString());
    }

    private static void deleteRecursively(Path directory) {
        try {
            FileSystemUtils.deleteRecursively(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + directory, e);
        }
    }

}
