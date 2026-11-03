package com.thinkxecai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application's messages stay English on a machine set to another language (ADR 056). The driver's
 * bundle is looked up as the PostgreSQL driver does it (its {@code GT} class, once, by the display locale).
 */
class ThinkXecAiApplicationLocaleTest {

    private static final String DRIVER_MESSAGES = "org.postgresql.translation.messages";

    private final Locale display = Locale.getDefault(Locale.Category.DISPLAY);
    private final Locale format = Locale.getDefault(Locale.Category.FORMAT);

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(Locale.Category.DISPLAY, display);
        Locale.setDefault(Locale.Category.FORMAT, format);
    }

    @Test
    void onAChineseSystemTheDriverWouldTranslateItsMessages() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);

        ResourceBundle bundle = ResourceBundle.getBundle(DRIVER_MESSAGES, Locale.getDefault(Locale.Category.DISPLAY));

        assertThat(bundle.getString("Position: {0}")).isNotEqualTo("Position: {0}");
    }

    @Test
    void afterStartupTheDriverFindsNoTranslationAndKeepsItsEnglishText() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);

        ThinkXecAiApplication.useEnglishMessages();

        assertThat(Locale.getDefault(Locale.Category.DISPLAY)).isEqualTo(Locale.ENGLISH);
        assertThat(Locale.getDefault(Locale.Category.FORMAT)).isEqualTo(Locale.ENGLISH);
        assertThatThrownBy(() -> ResourceBundle.getBundle(DRIVER_MESSAGES, Locale.getDefault(Locale.Category.DISPLAY)))
                .isInstanceOf(MissingResourceException.class);
    }

}
