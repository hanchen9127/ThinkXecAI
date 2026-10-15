package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the sender against a local fake of Google's token endpoint and the Gmail API; no network. */
class GmailSenderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);
    private static final String TOKEN_PATH = "/token";
    private static final String SEND_PATH = "/gmail/v1/users/me/messages/send";

    private record Recorded(String method, String path, Map<String, List<String>> headers, String body) {
    }

    @FunctionalInterface
    private interface Answer {
        void answer(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private volatile Answer tokenAnswer;
    private volatile Answer sendAnswer;
    private GmailSender sender;

    @BeforeEach
    void startFakeGoogle() throws IOException {
        tokenAnswer = exchange -> respond(exchange, 200, "{\"access_token\":\"tok-1\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
        sendAnswer = exchange -> respond(exchange, 200, "{\"id\":\"msg-1\",\"threadId\":\"t-1\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            try {
                (TOKEN_PATH.equals(exchange.getRequestURI().getPath()) ? tokenAnswer : sendAnswer).answer(exchange);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // the client gave up (timeout tests)
            }
        });
        server.start();
        warmUpHttpClient();
        sender = sender("bot@example.com");
    }

    /**
     * The first JDK HttpClient request in a JVM spends time loading classes, which on a busy machine
     * can exceed the short test deadline; one request beforehand keeps that out of the timed tests.
     */
    private void warmUpHttpClient() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/warm-up")).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new IllegalStateException("Fake Google server did not answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        requests.clear();
    }

    @AfterEach
    void stopFakeGoogle() {
        server.stop(0);
    }

    @Test
    void sendsTheMessageWithAFreshAccessTokenAndReturnsGmailsId() throws Exception {
        String id = sender.send(new InternetAddress("alice@example.com"), "Weekly report ✓ 周报", "Line one\nZweite Zeile: äöü");

        assertThat(id).isEqualTo("msg-1");
        assertThat(requests).hasSize(2);

        Recorded token = requests.get(0);
        assertThat(token.method()).isEqualTo("POST");
        assertThat(token.path()).isEqualTo(TOKEN_PATH);
        assertThat(token.headers().get("Content-type")).containsExactly("application/x-www-form-urlencoded");
        assertThat(form(token.body())).containsExactlyInAnyOrderEntriesOf(Map.of(
                "grant_type", "refresh_token", "client_id", "client-1",
                "client_secret", "secret-1", "refresh_token", "refresh-1"));

        Recorded send = requests.get(1);
        assertThat(send.method()).isEqualTo("POST");
        assertThat(send.path()).isEqualTo(SEND_PATH);
        assertThat(send.headers().get("Authorization")).containsExactly("Bearer tok-1");
        String raw = JSON.readTree(send.body()).get("raw").asText();
        assertThat(raw).doesNotContain("+", "/", "=");

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("bot@example.com");
        assertThat(message.getRecipients(MimeMessage.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("Weekly report ✓ 周报");
        // MIME text uses CRLF line endings on the wire.
        assertThat(((String) message.getContent()).replace("\r\n", "\n")).isEqualTo("Line one\nZweite Zeile: äöü");
    }

    @Test
    void theAccessTokenIsReusedUntilShortlyBeforeItExpires() throws Exception {
        sender.send(new InternetAddress("alice@example.com"), "One", "1");
        sender.send(new InternetAddress("alice@example.com"), "Two", "2");

        assertThat(requests).extracting(Recorded::path).containsExactly(TOKEN_PATH, SEND_PATH, SEND_PATH);
    }

    @Test
    void aTokenThatExpiresWithinAMinuteIsRefreshedForTheNextSend() throws Exception {
        tokenAnswer = exchange -> respond(exchange, 200, "{\"access_token\":\"tok-short\",\"expires_in\":30}");

        sender.send(new InternetAddress("alice@example.com"), "One", "1");
        sender.send(new InternetAddress("alice@example.com"), "Two", "2");

        assertThat(requests).extracting(Recorded::path).containsExactly(TOKEN_PATH, SEND_PATH, TOKEN_PATH, SEND_PATH);
    }

    @Test
    void aRevokedRefreshTokenIsReportedAndNothingIsSent() {
        tokenAnswer = exchange -> respond(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .isInstanceOf(GmailSender.SendFailure.class)
                .hasMessage("Not sent: Google rejected the stored refresh token (expired or revoked); a new refresh token is needed.");
        assertThat(requests).extracting(Recorded::path).containsExactly(TOKEN_PATH);
    }

    @Test
    void otherTokenErrorsNameTheStatusButNoSecrets() {
        tokenAnswer = exchange -> respond(exchange, 401, "{\"error\":\"invalid_client\"}");

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Not sent: Google refused an access token (HTTP 401, invalid_client).")
                .message().doesNotContain("secret-1", "refresh-1", "client-1");
    }

    @Test
    void aGmailErrorIsReportedWithGooglesMessage() {
        sendAnswer = exchange -> respond(exchange, 400, "{\"error\":{\"code\":400,\"message\":\"Invalid To header\"}}");

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Not sent: Gmail rejected the message (HTTP 400: Invalid To header).");
    }

    @Test
    void anUnauthorizedSendDropsTheCachedTokenSoTheNextSendRefreshesIt() throws Exception {
        sendAnswer = exchange -> respond(exchange, 401, "{}");
        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Not sent: Gmail rejected the message (HTTP 401).");

        sendAnswer = exchange -> respond(exchange, 200, "{\"id\":\"msg-2\"}");
        assertThat(sender.send(new InternetAddress("alice@example.com"), "S", "B")).isEqualTo("msg-2");
        assertThat(requests).extracting(Recorded::path).containsExactly(TOKEN_PATH, SEND_PATH, TOKEN_PATH, SEND_PATH);
    }

    @Test
    void aSlowTokenEndpointStopsTheSendWithinTheDeadline() {
        tokenAnswer = exchange -> {
            Thread.sleep(5_000);
            respond(exchange, 200, "{\"access_token\":\"late\",\"expires_in\":3599}");
        };
        Instant start = Instant.now();

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Not sent: Google did not issue an access token within 2 seconds.");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(3_500));
        assertThat(requests).extracting(Recorded::path).containsExactly(TOKEN_PATH);
    }

    /** The token request and the send share one deadline, so time spent on the token shortens the send's. */
    @Test
    void aSlowSendIsReportedAsPossiblySentWithinTheSameDeadline() {
        tokenAnswer = exchange -> {
            Thread.sleep(1_200);
            respond(exchange, 200, "{\"access_token\":\"tok-1\",\"expires_in\":3599}");
        };
        sendAnswer = exchange -> {
            Thread.sleep(5_000);
            respond(exchange, 200, "{\"id\":\"late\"}");
        };
        Instant start = Instant.now();

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Gmail did not answer within 2 seconds. The email may or may not have been sent; check before trying again.");
        // About 2 s in total; two full seconds for the send on top of the token's 1.2 s would take 3.2 s.
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(2_700));
    }

    @Test
    void anUnreachableServerIsReported() {
        server.stop(0);

        assertThatThrownBy(() -> sender.send(new InternetAddress("alice@example.com"), "S", "B"))
                .hasMessage("Not sent: Google's token service could not be reached.");
    }

    @Test
    void missingSettingsAreNamedInOrder() {
        GmailSender unconfigured = new GmailSender(" ", "client-1", "", "refresh-1",
                "http://127.0.0.1:1/token", "http://127.0.0.1:1", TEST_TIMEOUT);

        assertThat(unconfigured.missingSettings())
                .containsExactly("THINKXECAI_GMAIL_SENDER", "THINKXECAI_GMAIL_CLIENT_SECRET");
        assertThat(sender.missingSettings()).isEmpty();
    }

    private GmailSender sender(String from) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GmailSender(from, "client-1", "secret-1", "refresh-1", base + TOKEN_PATH, base + "/", TEST_TIMEOUT);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> form(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

}
