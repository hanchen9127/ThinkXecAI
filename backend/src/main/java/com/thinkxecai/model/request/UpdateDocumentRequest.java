package com.thinkxecai.model.request;

import lombok.Data;

/**
 * Only the name can change. Metadata is server-managed (it holds the stored file's path), and the
 * knowledge base is fixed because the document's chunks carry it too.
 */
@Data
public class UpdateDocumentRequest {

    @NullOrNotBlank
    private String name;

}
