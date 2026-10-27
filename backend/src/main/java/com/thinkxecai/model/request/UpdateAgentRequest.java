package com.thinkxecai.model.request;

import com.thinkxecai.model.ChatModelNames;
import com.thinkxecai.model.ChatOptionsInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** PATCH semantics: a null field is left unchanged; chatOptions is merged field by field. */
@Data
public class UpdateAgentRequest {

    @NullOrNotBlank
    @Size(max = RequestLimits.NAME)
    private String name;
    @Size(max = RequestLimits.DESCRIPTION)
    private String description;
    @Size(max = RequestLimits.TEXT)
    private String systemPrompt;
    @Pattern(regexp = ChatModelNames.PATTERN, message = ChatModelNames.PATTERN_MESSAGE)
    @Schema(allowableValues = {ChatModelNames.DEEPSEEK_CHAT, ChatModelNames.GEMINI_FLASH, ChatModelNames.QWEN_FLASH})
    private String model;
    /** Each name must be an optional tool (checked by the service); duplicates are dropped. */
    @Size(max = RequestLimits.LIST)
    private List<@NotNull String> allowedTools;
    /** Each id must refer to an existing knowledge base (checked by the service). */
    @Size(max = RequestLimits.LIST)
    private List<@NotNull UUID> allowedKbs;
    @Valid
    private ChatOptionsInput chatOptions;

}
