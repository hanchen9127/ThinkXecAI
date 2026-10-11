package com.thinkxecai.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Multipart form of POST /documents/upload: one Markdown file for one knowledge base. */
@Data
public class UploadDocumentRequest {

    /** Must refer to an existing knowledge base (checked by the service). */
    @NotNull
    private UUID kbId;
    @NotNull
    private MultipartFile file;

}
