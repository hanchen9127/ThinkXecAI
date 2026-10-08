package com.thinkxecai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-agent runtime options, as stored and returned: all three fields are always present.
 * temperature/topP are stored only; they are not applied to the runtime Spring AI ChatOptions in
 * this target state. messageLength caps how many recent persisted messages are loaded into memory.
 * <p>
 * Requests send a {@link ChatOptionsInput}, which may be partial. The defaults match the
 * chat_options column default in db/init/001_thinkxecai.sql.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatOptions {

    private Double temperature;
    private Double topP;
    private Integer messageLength;

    public static ChatOptions defaults() {
        return new ChatOptions(0.7, 1.0, 10);
    }

    /** A copy of these options with every non-null field of {@code overrides} applied; null overrides change nothing. */
    public ChatOptions overriddenBy(ChatOptionsInput overrides) {
        if (overrides == null) {
            return new ChatOptions(temperature, topP, messageLength);
        }
        return new ChatOptions(
                overrides.getTemperature() != null ? overrides.getTemperature() : temperature,
                overrides.getTopP() != null ? overrides.getTopP() : topP,
                overrides.getMessageLength() != null ? overrides.getMessageLength() : messageLength);
    }

}
