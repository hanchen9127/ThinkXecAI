package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Sends one email through the Gmail API ({@code users.messages.send}) as the configured sender.
 * <p>
 * The OAuth access token comes from a standard {@code refresh_token} request to Google's token endpoint
 * and is reused until a minute before it expires. The refresh token was granted only the
 * {@code gmail.send} scope, so it cannot read or delete mail. The token request and the send share
 * one deadline (10 s), so a slow Google answer never holds the agent for longer (ADR 027).
 * <p>
 * Failures are thrown as {@link SendFailure}, whose message is written for the model: it never
 * contains tokens, secrets, or the message body.
 */
@Slf4j
@Component
public class GmailSender {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, String> settings;
    private final URI tokenUri;
    private final URI sendUri;
    private final Duration timeout;
    private final HttpClient httpClient;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.MIN;

    @Autowired
    public GmailSender(@Value("${thinkxecai.mail.gmail.sender:}") String sender,
                       @Value("${thinkxecai.mail.gmail.client-id:}") String clientId,
                       @Value("${thinkxecai.mail.gmail.client-secret:}") String clientSecret,
                       @Value("${thinkxecai.mail.gmail.refresh-token:}") String refreshToken,
                       @Value("${thinkxecai.mail.gmail.token-uri}") String tokenUri,
                       @Value("${thinkxecai.mail.gmail.api-base-url}") String apiBaseUrl) {
        this(sender, clientId, clientSecret, refreshToken, tokenUri, apiBaseUrl, TIMEOUT);
    }

    /** With a shorter timeout, so tests of slow servers do not take 10 seconds each. */
    GmailSender(String sender, String clientId, String clientSecret, String refreshToken,
                String tokenUri, String apiBaseUrl, Duration timeout) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("THINKXECAI_GMAIL_SENDER", sender.trim());
        values.put("THINKXECAI_GMAIL_CLIENT_ID", clientId.trim());
        values.put("THINKXECAI_GMAIL_CLIENT_SECRET", clientSecret.trim());
        values.put("THINKXECAI_GMAIL_REFRESH_TOKEN", refreshToken.trim());
        this.settings = values;
        this.tokenUri = URI.create(tokenUri);
        this.sendUri = URI.create(apiBaseUrl.replaceAll("/+$", "") + "/gmail/v1/users/me/messages/send");
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    /** Names of the Gmail environment variables that are not set; empty when sending is configured. */
    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        settings.forEach((name, value) -> {
            if (!StringUtils.hasText(value)) {
                missing.add(name);
            }
        });
        return missing;
    }

    /**
     * Sends a plain-text UTF-8 email and returns Gmail's message id.
     *
     * @throws SendFailure with a message for the model when the email was not (or may not have been) sent
     */
    public String send(InternetAddress to, String subject, String body) {
        Instant deadline = Instant.now().plus(timeout);
        String raw = encode(mimeMessage(to, subject, body));
        String token = accessToken(deadline);

        HttpRequest request = HttpRequest.newBuilder(sendUri)
                .timeout(remaining(deadline, "Not sent: the Gmail request could not start within the time limit."))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.createObjectNode().put("raw", raw).toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("Gmail send timed out after {}", timeout);
            throw new SendFailure("Gmail did not answer within " + timeout.toSeconds()
                    + " seconds. The email may or may not have been sent; check before trying again.");
        } catch (IOException e) {
            log.warn("Gmail send failed: {}", e.toString());
            throw new SendFailure("Not sent: Gmail could not be reached.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SendFailure("Not sent: sending was interrupted.");
        }

        if (response.statusCode() / 100 != 2) {
            if (response.statusCode() == 401) {
                forgetAccessToken();
            }
            log.warn("Gmail rejected the message: HTTP {}", response.statusCode());
            throw new SendFailure("Not sent: Gmail rejected the message (HTTP " + response.statusCode()
                    + googleMessage(response.body(), "/error/message") + ").");
        }
        return readJson(response.body()).path("id").asText("");
    }

    /** A cached access token, or a new one from the refresh token. Synchronized: one refresh at a time. */
    private synchronized String accessToken(Instant deadline) {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minus(EXPIRY_MARGIN))) {
            return accessToken;
        }
        String form = Map.of(
                        "grant_type", "refresh_token",
                        "client_id", settings.get("THINKXECAI_GMAIL_CLIENT_ID"),
                        "client_secret", settings.get("THINKXECAI_GMAIL_CLIENT_SECRET"),
                        "refresh_token", settings.get("THINKXECAI_GMAIL_REFRESH_TOKEN"))
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(remaining(deadline, "Not sent: no time left to get an access token."))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("Google token request timed out after {}", timeout);
            throw new SendFailure("Not sent: Google did not issue an access token within "
                    + timeout.toSeconds() + " seconds.");
        } catch (IOException e) {
            log.warn("Google token request failed: {}", e.toString());
            throw new SendFailure("Not sent: Google's token service could not be reached.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SendFailure("Not sent: sending was interrupted.");
        }

        JsonNode json = readJson(response.body());
        if (response.statusCode() / 100 != 2) {
            String error = json.path("error").asText("");
            log.warn("Google token request rejected: HTTP {} {}", response.statusCode(), error);
            if ("invalid_grant".equals(error)) {
                throw new SendFailure("Not sent: Google rejected the stored refresh token (expired or revoked); "
                        + "a new refresh token is needed.");
            }
            throw new SendFailure("Not sent: Google refused an access token (HTTP " + response.statusCode()
                    + (error.isEmpty() ? "" : ", " + error) + ").");
        }
        String token = json.path("access_token").asText("");
        if (token.isEmpty()) {
            throw new SendFailure("Not sent: Google's token answer had no access token.");
        }
        accessToken = token;
        accessTokenExpiresAt = Instant.now().plusSeconds(json.path("expires_in").asLong(0));
        return token;
    }

    private synchronized void forgetAccessToken() {
        accessToken = null;
        accessTokenExpiresAt = Instant.MIN;
    }

    private MimeMessage mimeMessage(InternetAddress to, String subject, String body) {
        try {
            // With mail.from set, the Message-ID uses the sender's domain. Without it Jakarta Mail looks up
            // this machine's host name: a reverse DNS lookup that can take seconds of the send's deadline,
            // and the name would reach the recipient.
            Properties properties = new Properties();
            properties.setProperty("mail.from", settings.get("THINKXECAI_GMAIL_SENDER"));
            MimeMessage message = new MimeMessage(Session.getInstance(properties));
            message.setFrom(new InternetAddress(settings.get("THINKXECAI_GMAIL_SENDER"), true));
            message.setRecipient(MimeMessage.RecipientType.TO, to);
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            return message;
        } catch (MessagingException e) {
            throw new SendFailure("Not sent: the message could not be built (check THINKXECAI_GMAIL_SENDER).");
        }
    }

    /** The whole RFC 822 message, base64url-encoded without padding, as the Gmail API's {@code raw} field. */
    private static String encode(MimeMessage message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } catch (IOException | MessagingException e) {
            throw new SendFailure("Not sent: the message could not be encoded.");
        }
    }

    private static Duration remaining(Instant deadline, String messageWhenNone) {
        Duration left = Duration.between(Instant.now(), deadline);
        if (left.isNegative() || left.isZero()) {
            throw new SendFailure(messageWhenNone);
        }
        return left;
    }

    private static JsonNode readJson(String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException e) {
            return JSON.createObjectNode();
        }
    }

    /** Google's own short error text, if the body has one, for the model to act on. */
    private static String googleMessage(String body, String pointer) {
        String message = readJson(body).at(pointer).asText("");
        if (message.isEmpty()) {
            return "";
        }
        return ": " + (message.length() > 200 ? message.substring(0, 200) + "…" : message);
    }

    /** The email was not sent, or might not have been; the message is meant for the model. */
    public static class SendFailure extends RuntimeException {
        public SendFailure(String message) {
            super(message);
        }
    }

}
