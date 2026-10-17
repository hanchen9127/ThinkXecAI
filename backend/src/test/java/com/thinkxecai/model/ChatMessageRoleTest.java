package com.thinkxecai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.converter.ChatMessageConverter;
import com.thinkxecai.model.entity.ChatMessageEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role names must not depend on the machine's language: in Turkish, upper-casing "assistant" gives
 * "ASSİSTANT" (dotted capital I) and lower-casing "ASSISTANT" gives "assıstant", which match neither the
 * enum nor the database's CHECK constraint.
 */
class ChatMessageRoleTest {

    private Locale original;

    @BeforeEach
    void useTurkish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restore() {
        Locale.setDefault(original);
    }

    @Test
    void jsonNamesAreTheSameInEveryLocale() {
        assertThat(ChatMessageRole.ASSISTANT.toJson()).isEqualTo("assistant");
        assertThat(ChatMessageRole.fromJson("assistant")).isEqualTo(ChatMessageRole.ASSISTANT);
    }

    @Test
    void storedRolesAreReadInEveryLocale() {
        ChatMessageConverter converter = new ChatMessageConverter(new ObjectMapper());

        assertThat(converter.toDTO(ChatMessageEntity.builder().role("assistant").build()).getRole())
                .isEqualTo(ChatMessageRole.ASSISTANT);
    }

}
