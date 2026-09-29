package com.thinkxecai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-agent runtime options. temperature/topP are stored only; they are not applied to the
 * runtime Spring AI ChatOptions in this target state. messageLength caps how many recent
 * persisted messages are loaded into memory.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatOptions {

    @Builder.Default
    private Double temperature = 0.7;

    @Builder.Default
    private Double topP = 1.0;

    @Builder.Default
    private Integer messageLength = 10;

}
