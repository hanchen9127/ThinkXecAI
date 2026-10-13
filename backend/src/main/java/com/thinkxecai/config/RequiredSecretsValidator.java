package com.thinkxecai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Stops startup when a secret the application cannot work without is missing. The Gmail settings are
 * deliberately not checked here: email is optional, and the email tool reports missing settings at
 * its first send instead.
 */
@Component
public class RequiredSecretsValidator {

    @Value("${THINKXECAI_DB_PASSWORD:}")
    private String dbPassword;

    @Value("${THINKXECAI_GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${THINKXECAI_DEEPSEEK_API_KEY:}")
    private String deepSeekApiKey;

    @PostConstruct
    public void validate() {
        require("THINKXECAI_DB_PASSWORD", dbPassword);
        require("THINKXECAI_GEMINI_API_KEY", geminiApiKey);
        require("THINKXECAI_DEEPSEEK_API_KEY", deepSeekApiKey);
    }

    private void require(String variableName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + variableName
                            + ". Set it in your local .env file (see .env.example).");
        }
    }

}
