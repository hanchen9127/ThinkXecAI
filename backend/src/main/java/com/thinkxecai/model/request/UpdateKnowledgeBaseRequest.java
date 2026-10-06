package com.thinkxecai.model.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** PATCH semantics: a null field is left unchanged. */
@Data
public class UpdateKnowledgeBaseRequest {

    /** Optional, but when present it must not be blank (@NotBlank would also reject null). */
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
    private String name;
    private String description;

}
