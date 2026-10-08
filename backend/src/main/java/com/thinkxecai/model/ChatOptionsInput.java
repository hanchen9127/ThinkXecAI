package com.thinkxecai.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat options as sent in agent requests: any field may be missing (null). A new agent gets
 * {@link ChatOptions#defaults()} for the missing fields; an update keeps the current values
 * ({@link ChatOptions#overriddenBy}). Separate from {@link ChatOptions}, whose fields are always
 * present, so the API description can tell the two apart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatOptionsInput {

    @DecimalMin(value = "0.0", message = "must be between 0 and 2")
    @DecimalMax(value = "2.0", message = "must be between 0 and 2")
    private Double temperature;

    @DecimalMin(value = "0.0", message = "must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "must be between 0 and 1")
    private Double topP;

    @Min(value = 1, message = "must be between 1 and 100")
    @Max(value = 100, message = "must be between 1 and 100")
    private Integer messageLength;

}
