package com.thinkxecai.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** PATCH semantics: a null field is left unchanged. */
@Data
public class UpdateKnowledgeBaseRequest {

    @NullOrNotBlank
    @Size(max = RequestLimits.NAME)
    private String name;
    @Size(max = RequestLimits.DESCRIPTION)
    private String description;

}
