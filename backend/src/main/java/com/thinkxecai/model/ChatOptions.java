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
 * Per-agent runtime options. temperature/topP are stored only; they are not applied to the
 * runtime Spring AI ChatOptions in this target state. messageLength caps how many recent
 * persisted messages are loaded into memory.
 * <p>
 * In requests a null field means "not given": a new agent gets the default, an update keeps the
 * current value ({@link #overriddenBy}). Stored and returned options always have all three fields.
 * The defaults match the chat_options column default in db/init/001_thinkxecai.sql.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatOptions {

    @DecimalMin(value = "0.0", message = "must be between 0 and 2")
    @DecimalMax(value = "2.0", message = "must be between 0 and 2")
    private Double temperature;

    @DecimalMin(value = "0.0", message = "must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "must be between 0 and 1")
    private Double topP;

    @Min(value = 1, message = "must be between 1 and 100")
    @Max(value = 100, message = "must be between 1 and 100")
    private Integer messageLength;

    public static ChatOptions defaults() {
        return new ChatOptions(0.7, 1.0, 10);
    }

    /** A copy of these options with every non-null field of {@code overrides} applied; null overrides change nothing. */
    public ChatOptions overriddenBy(ChatOptions overrides) {
        if (overrides == null) {
            return new ChatOptions(temperature, topP, messageLength);
        }
        return new ChatOptions(
                overrides.temperature != null ? overrides.temperature : temperature,
                overrides.topP != null ? overrides.topP : topP,
                overrides.messageLength != null ? overrides.messageLength : messageLength);
    }

}
