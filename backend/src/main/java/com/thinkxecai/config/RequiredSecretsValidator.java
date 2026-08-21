package com.thinkxecai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequiredSecretsValidator {

    @Value("${THINKXECAI_DB_PASSWORD:}")
    private String dbPassword;

    @Value("${THINKXECAI_MAIL_USERNAME:}")
    private String mailUsername;

    @Value("${THINKXECAI_MAIL_PASSWORD:}")
    private String mailPassword;

    @Value("${THINKXECAI_OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${THINKXECAI_DEEPSEEK_API_KEY:}")
    private String deepSeekApiKey;

    @PostConstruct
    public void validate() {
        require("THINKXECAI_DB_PASSWORD", dbPassword);
        require("THINKXECAI_MAIL_USERNAME", mailUsername);
        require("THINKXECAI_MAIL_PASSWORD", mailPassword);
        require("THINKXECAI_OPENAI_API_KEY", openAiApiKey);
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
