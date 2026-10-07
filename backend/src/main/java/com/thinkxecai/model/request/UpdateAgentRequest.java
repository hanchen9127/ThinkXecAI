package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** PATCH semantics: a null field is left unchanged; chatOptions is merged field by field. */
@Data
public class UpdateAgentRequest {

    @NullOrNotBlank
    private String name;
    private String description;
    private String systemPrompt;
    @Pattern(regexp = CreateAgentRequest.MODELS, message = CreateAgentRequest.MODELS_MESSAGE)
    private String model;
    /** Not validated until the tool system exists (Phase 9). */
    private List<String> allowedTools;
    /** Each id must refer to an existing knowledge base (checked by the service). */
    private List<@NotNull UUID> allowedKbs;
    @Valid
    private ChatOptions chatOptions;

}
