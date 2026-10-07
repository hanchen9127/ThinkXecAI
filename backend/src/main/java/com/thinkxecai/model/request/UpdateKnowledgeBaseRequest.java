package com.thinkxecai.model.request;

import lombok.Data;

/** PATCH semantics: a null field is left unchanged. */
@Data
public class UpdateKnowledgeBaseRequest {

    @NullOrNotBlank
    private String name;
    private String description;

}
