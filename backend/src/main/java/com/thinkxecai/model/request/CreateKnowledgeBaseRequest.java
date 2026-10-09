package com.thinkxecai.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    @NotBlank
    private String name;
    private String description;

}
