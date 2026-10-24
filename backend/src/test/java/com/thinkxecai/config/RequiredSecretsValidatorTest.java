package com.thinkxecai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Startup stops only for the secrets the application cannot work without; email settings are optional. */
class RequiredSecretsValidatorTest {

    private static final String[] REQUIRED = {
            "THINKXECAI_DB_PASSWORD=db", "THINKXECAI_GEMINI_API_KEY=gemini", "THINKXECAI_DEEPSEEK_API_KEY=deepseek",
            "THINKXECAI_QWEN_API_KEY=qwen"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RequiredSecretsValidator.class);

    @Test
    void startsWithTheRequiredSecretsAndNoEmailSettings() {
        runner.withPropertyValues(REQUIRED)
                .withPropertyValues("THINKXECAI_GMAIL_SENDER=", "THINKXECAI_GMAIL_CLIENT_ID=",
                        "THINKXECAI_GMAIL_CLIENT_SECRET=", "THINKXECAI_GMAIL_REFRESH_TOKEN=",
                        "THINKXECAI_MAIL_ALLOWED_RECIPIENTS=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"THINKXECAI_DB_PASSWORD", "THINKXECAI_GEMINI_API_KEY", "THINKXECAI_DEEPSEEK_API_KEY",
            "THINKXECAI_QWEN_API_KEY"})
    void aMissingOrBlankRequiredSecretStopsStartupAndIsNamed(String variable) {
        runner.withPropertyValues(REQUIRED)
                .withPropertyValues(variable + "= ")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Missing required environment variable: " + variable));
    }

}
