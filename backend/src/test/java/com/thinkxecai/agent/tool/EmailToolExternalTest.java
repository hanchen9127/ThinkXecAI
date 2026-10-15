package com.thinkxecai.agent.tool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sends one real email through Gmail to the first allowlisted address (needs the four
 * {@code THINKXECAI_GMAIL_*} variables and {@code THINKXECAI_MAIL_ALLOWED_RECIPIENTS}; skipped otherwise).
 */
@Tag("external")
@SpringBootTest
class EmailToolExternalTest {

    @Autowired
    private EmailTool emailTool;

    @Autowired
    private GmailSender gmailSender;

    @Value("${thinkxecai.mail.allowed-recipients:}")
    private String allowedRecipients;

    @Test
    void sendsARealEmailToAnAllowedRecipient() {
        String recipient = allowedRecipients.split(",")[0].trim();
        assumeTrue(gmailSender.missingSettings().isEmpty() && !recipient.isEmpty(), "Gmail is not configured");

        String answer = emailTool.sendEmail(recipient, "ThinkXecAI email tool test",
                "Sent by EmailToolExternalTest at " + Instant.now() + ".");

        assertThat(answer).startsWith("Sent to " + recipient + " (Gmail message id ");
    }

}
