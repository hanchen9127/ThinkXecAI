package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatModelNames;
import com.thinkxecai.model.ChatOptionsInput;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @NotBlank
    private String name;
    private String description;
    private String systemPrompt;
    @NotNull
    @Pattern(regexp = ChatModelNames.PATTERN, message = ChatModelNames.PATTERN_MESSAGE)
    @Schema(allowableValues = {ChatModelNames.DEEPSEEK_CHAT, ChatModelNames.GEMINI_FLASH})
    private String model;
    /** Not validated until the tool system exists (Phase 9). */
    private List<String> allowedTools;
    /** Each id must refer to an existing knowledge base (checked by the service). */
    private List<@NotNull UUID> allowedKbs;
    @Valid
    private ChatOptionsInput chatOptions;

}
