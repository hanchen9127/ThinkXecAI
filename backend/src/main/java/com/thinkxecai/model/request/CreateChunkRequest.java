package com.thinkxecai.model.request;

import lombok.Data;

/** Used internally during Markdown ingestion; the embedding is computed by the ingestion service. */
@Data
public class CreateChunkRequest {

    private String kbId;
    private String docId;
    private String title;
    private String content;

}
