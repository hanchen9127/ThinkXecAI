package com.thinkxecai.model;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names an agent request may carry (the {@code @Pattern}) are exactly the names the ChatClient
 * registry requires, so a saved agent's model always resolves.
 */
class ChatModelNamesTest {

    @Test
    void theRequestPatternAcceptsExactlyTheRegisteredNames() {
        String expected = ChatModelNames.ALL.stream()
                .map(name -> name.replace(".", "\\."))
                .collect(Collectors.joining("|"));

        assertThat(ChatModelNames.PATTERN).isEqualTo(expected);
        assertThat(ChatModelNames.ALL).allMatch(name -> name.matches(ChatModelNames.PATTERN));
        assertThat("gemini-3x8-flash").doesNotMatch(ChatModelNames.PATTERN);
    }

    @Test
    void theValidationMessageNamesEveryModel() {
        assertThat(ChatModelNames.PATTERN_MESSAGE).contains(ChatModelNames.ALL);
    }

}
