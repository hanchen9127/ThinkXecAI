package com.thinkxecai.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    @NotBlank
    @Size(max = RequestLimits.NAME)
    private String name;
    @Size(max = RequestLimits.DESCRIPTION)
    private String description;

}
