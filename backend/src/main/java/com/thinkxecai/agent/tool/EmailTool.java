package com.thinkxecai.agent.tool;

import com.thinkxecai.model.ToolFailureKind;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends an email for the agent through {@link GmailSender}, in this order: the arguments are checked,
 * then the Gmail settings (optional, so a missing one is reported here rather than at startup), then
 * the recipient allowlist, and only then is the email sent and the real outcome returned (ADR 027).
 * <p>
 * The allowlist is fail-closed: an empty list refuses every send. The recipient is parsed strictly as
 * exactly one address, and that address (not the raw text) is compared with the list, so a comma
 * list or a display name cannot smuggle in another recipient. Every outcome is text for the model: a send
 * that did not happen throws {@link ToolCallFailure} with that text and its kind (ADR 064), which never ends
 * the agent loop, and refusals never reveal which addresses are allowed.
 */
@Slf4j
@Component
public class EmailTool implements Tool {

    private final GmailSender gmailSender;
    private final Set<String> allowedRecipients;

    public EmailTool(GmailSender gmailSender,
                     @Value("${thinkxecai.mail.allowed-recipients:}") String allowedRecipients) {
        this.gmailSender = gmailSender;
        this.allowedRecipients = Arrays.stream(allowedRecipients.split(","))
                .map(address -> address.trim().toLowerCase(Locale.ROOT))
                .filter(address -> !address.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getName() {
        return "emailTool";
    }

    @Override
    public String getDescription() {
        return "Sends a plain-text email through Gmail to an allowed recipient.";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "sendEmail",
            description = "Send a plain-text email to one recipient. Only some recipients are allowed; "
                    + "the answer says whether the email was sent.")
    public String sendEmail(@ToolParam(description = "One recipient email address") String to,
                            @ToolParam(description = "Subject, one line") String subject,
                            @ToolParam(description = "Plain-text body") String content) {
        if (isBlank(to) || isBlank(subject) || isBlank(content)) {
            throw new ToolCallFailure(ToolFailureKind.INVALID_ARGUMENTS, "Not sent: to, subject, and content must all be given.");
        }
        if (subject.contains("\r") || subject.contains("\n")) {
            throw new ToolCallFailure(ToolFailureKind.INVALID_ARGUMENTS, "Not sent: the subject must be a single line.");
        }
        InternetAddress recipient = parseOneAddress(to);
        if (recipient == null) {
            throw new ToolCallFailure(ToolFailureKind.INVALID_ARGUMENTS, "Not sent: \"to\" must be exactly one email address.");
        }

        List<String> missing = gmailSender.missingSettings();
        if (!missing.isEmpty()) {
            log.warn("Email not sent: Gmail is not configured (missing {})", missing);
            throw new ToolCallFailure(ToolFailureKind.TOOL_ERROR,
                    "Not sent: email is not configured on this server. Missing environment variables: "
                            + String.join(", ", missing) + ".");
        }
        if (!allowedRecipients.contains(recipient.getAddress().toLowerCase(Locale.ROOT))) {
            log.warn("Email refused: {} is not an allowed recipient{}", recipient.getAddress(),
                    allowedRecipients.isEmpty() ? " (the allowlist is empty)" : "");
            throw new ToolCallFailure(ToolFailureKind.REFUSED, "Not sent: " + recipient.getAddress() + " is not an allowed recipient.");
        }

        try {
            String messageId = gmailSender.send(recipient, subject.trim(), content);
            log.info("Email sent to {} (Gmail message id {})", recipient.getAddress(), messageId);
            return "Sent to " + recipient.getAddress() + " (Gmail message id " + messageId + ").";
        } catch (GmailSender.SendFailure e) {
            throw new ToolCallFailure(ToolFailureKind.TOOL_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Email sending failed", e);
            throw new ToolCallFailure(ToolFailureKind.TOOL_ERROR, "Not sent: an internal error occurred.");
        }
    }

    /**
     * Exactly one plain address (a display name is allowed and dropped); null for lists, groups, or
     * anything that is not a valid address.
     */
    private static InternetAddress parseOneAddress(String text) {
        try {
            InternetAddress[] addresses = InternetAddress.parse(text.trim(), true);
            if (addresses.length != 1 || addresses[0].isGroup()) {
                return null;
            }
            InternetAddress address = new InternetAddress(addresses[0].getAddress(), true);
            address.validate();
            return address;
        } catch (AddressException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
