package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatOptionsInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Omitted lists default to empty and omitted chatOptions fields to {@link com.thinkxecai.model.ChatOptions#defaults()}. */
@Data
public class CreateAgentRequest {

    /** The chat models registered in Phase 10; keep in sync with UpdateAgentRequest. */
    static final String MODELS = "deepseek-chat|gemini-3\\.8-flash";
    static final String MODELS_MESSAGE = "must be deepseek-chat or gemini-3.8-flash";

    @NotBlank
    private String name;
    private String description;
    private String systemPrompt;
    @NotNull
    @Pattern(regexp = MODELS, message = MODELS_MESSAGE)
    private String model;
    /** Not validated until the tool system exists (Phase 9). */
    private List<String> allowedTools;
    /** Each id must refer to an existing knowledge base (checked by the service). */
    private List<@NotNull UUID> allowedKbs;
    @Valid
    private ChatOptionsInput chatOptions;

}
