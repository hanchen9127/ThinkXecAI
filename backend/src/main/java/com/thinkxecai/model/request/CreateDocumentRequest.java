package com.thinkxecai.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Creates a document record without a file (uploads arrive in Phase 8). There is no metadata field:
 * metadata is written only by the server, because it holds the stored file's path, which deleting a
 * document acts on.
 */
@Data
public class CreateDocumentRequest {

    /** Must refer to an existing knowledge base (checked by the service). */
    @NotNull
    private UUID kbId;
    @NotBlank
    private String name;

}
