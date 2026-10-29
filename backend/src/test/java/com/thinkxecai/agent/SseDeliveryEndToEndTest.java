package com.thinkxecai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.service.ServiceTestSupport;
import com.thinkxecai.service.SseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.thinkxecai.agent.ScriptedChatModel.call;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * Agent runs pushing their saved messages to a browser's stream, on a real port with the real listener,
 * scheduler, executor, and database and a scripted model. Each pushed message is the JSON the history returns
 * for it; a run never depends on a client being there (ISSUE-007). Rows are committed (runs read them on other
 * threads), so they are removed afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class SseDeliveryEndToEndTest extends ServiceTestSupport {

    private static final String MARKER = "sse-delivery-end-to-end-test";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private SseService sseService;

    @MockitoBean
    private ChatClientRegistry chatClientRegistry;

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final List<SseClient> clients = new ArrayList<>();
    private UUID session;

    @BeforeEach
    void agentAndSession() {
        when(chatClientRegistry.get("deepseek-chat")).thenReturn(ChatClient.builder(model).build());
        session = insertChatSession(insertAgent(MARKER), MARKER);
    }

    @AfterEach
    void closeStreamsAndRemoveCommittedRows() {
        clients.forEach(SseClient::close);
        sseService.close(session);
        http.close();
        jdbcTemplate.update("DELETE FROM chat_session WHERE title = ?", MARKER);
        jdbcTemplate.update("DELETE FROM agent WHERE name = ?", MARKER);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Opens the session's stream as a browser's EventSource does and waits for its {@code init} event. */
    private SseClient connect() throws Exception {
        SseClient client = new SseClient(uri("/sse/connect/" + session));
        clients.add(client);
        assertThat(client.status).isEqualTo(200);
        assertThat(client.next()).isEqualTo(new Event("init", "connected"));
        return client;
    }

    private void send(String content) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/chat-messages"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"sessionId\":\"" + session + "\",\"content\":\"" + content + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(JSON.readTree(response.body()).get("code").asInt()).isEqualTo(200);
    }

    /** The session's messages as {@code GET /api/chat-messages/session/{id}} returns them. */
    private List<JsonNode> history() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri("/api/chat-messages/session/" + session)).build(),
                HttpResponse.BodyHandlers.ofString());
        List<JsonNode> messages = new ArrayList<>();
        JSON.readTree(response.body()).get("data").forEach(messages::add);
        return messages;
    }

    private List<String> roles() {
        return jdbcTemplate.queryForList(
                "SELECT role FROM chat_message WHERE session_id = ? ORDER BY created_at", String.class, session);
    }

    private void awaitRows(int count) {
        await().atMost(WAIT).until(() -> roles().size() == count);
    }

    /** The message a {@code message} event carries, after checking the event's envelope. */
    private static JsonNode pushedMessage(Event event) throws Exception {
        assertThat(event.name()).isEqualTo("message");
        JsonNode json = JSON.readTree(event.data());
        assertThat(json.get("type").asText()).isEqualTo("AI_GENERATED_CONTENT");
        assertThat(json.get("metadata").get("chatMessageId")).isEqualTo(json.get("payload").get("message").get("id"));
        return json.get("payload").get("message");
    }

    /** A step that waits for the test, so something can happen while the run is between two steps. */
    private static Supplier<ChatResponse> after(CountDownLatch latch, String reply) {
        return () -> {
            try {
                assertThat(latch.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return ScriptedChatModel.reply(reply);
        };
    }

    @Test
    void aConnectedClientGetsEachSavedMessageInOrderAsTheHistoryReturnsIt() throws Exception {
        SseClient client = connect();
        model.thenCall("", call("d", "getDate", "{}")).thenReply("It is today.");

        send("What is the date?");

        List<JsonNode> pushed = List.of(pushedMessage(client.next()), pushedMessage(client.next()),
                pushedMessage(client.next()));
        awaitRows(4);
        // the user's own message is not pushed: the page that sent it has it already
        assertThat(pushed).containsExactlyElementsOf(history().subList(1, 4));
        assertThat(pushed).extracting(message -> message.get("role").asText()).containsExactly("assistant", "tool", "assistant");
    }

    @Test
    void aRunWithoutAClientSavesEverything() throws Exception {
        model.thenCall("", call("d", "getDate", "{}")).thenReply("It is today.");

        send("What is the date?");

        awaitRows(4);
        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
    }

    @Test
    void aClientThatGoesAwayMidRunLeavesTheRunToFinish(CapturedOutput output) throws Exception {
        SseClient client = connect();
        CountDownLatch closed = new CountDownLatch(1);
        model.thenCall("", call("d", "getDate", "{}")).then(after(closed, "It is today."));

        send("What is the date?");
        pushedMessage(client.next());
        pushedMessage(client.next());
        client.close();
        closed.countDown();

        awaitRows(4);
        assertThat(roles()).containsExactly("user", "assistant", "tool", "assistant");
        assertThat(output).doesNotContain("SSE connect failed");
    }

    @Test
    void aSecondClientTakesOverAndTheFirstIsToldSo() throws Exception {
        SseClient first = connect();
        CountDownLatch secondConnected = new CountDownLatch(1);
        model.thenCall("", call("d", "getDate", "{}")).then(after(secondConnected, "It is today."));

        send("What is the date?");
        pushedMessage(first.next());
        pushedMessage(first.next());
        SseClient second = connect();
        secondConnected.countDown();

        assertThat(first.next()).isEqualTo(new Event("replaced", "replaced"));
        assertThat(first.next()).isEqualTo(SseClient.END);
        JsonNode answer = pushedMessage(second.next());
        awaitRows(4);
        assertThat(answer).isEqualTo(history().get(3));
        assertThat(answer.get("content").asText()).isEqualTo("It is today.");
    }

    @Test
    void deletingTheSessionEndsItsStreamAndAReconnectIsNotFound() throws Exception {
        SseClient client = connect();

        HttpResponse<String> deleted = http.send(
                HttpRequest.newBuilder(uri("/api/chat-sessions/" + session)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(JSON.readTree(deleted.body()).get("code").asInt()).isEqualTo(200);

        // a normal end, after which a browser's EventSource reconnects (ADR 049)
        assertThat(client.next()).isEqualTo(SseClient.END);
        HttpResponse<String> reconnect = http.send(HttpRequest.newBuilder(uri("/sse/connect/" + session))
                        .header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(reconnect.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(reconnect.body()).get("message").asText()).isEqualTo("Chat session not found");
    }

    record Event(String name, String data) {
    }

    /** Stands in for a browser's EventSource: reads the stream on its own thread and queues each event. */
    private static final class SseClient implements AutoCloseable {

        static final Event END = new Event("end of stream", "");

        private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        final int status;

        SseClient(URI uri) throws Exception {
            HttpResponse<Stream<String>> response = http.send(
                    HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").build(),
                    HttpResponse.BodyHandlers.ofLines());
            status = response.statusCode();
            Thread.ofVirtual().start(() -> read(response.body()));
        }

        private void read(Stream<String> lines) {
            String name = null;
            StringBuilder data = new StringBuilder();
            try (lines) {
                for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                    String line = it.next();
                    if (line.startsWith("event:")) {
                        name = line.substring("event:".length());
                    } else if (line.startsWith("data:")) {
                        data.append(data.isEmpty() ? "" : "\n").append(line.substring("data:".length()));
                    } else if (line.isEmpty() && name != null) {
                        events.add(new Event(name, data.toString()));
                        name = null;
                        data.setLength(0);
                    }
                }
            } catch (RuntimeException e) {
                // closed by the test
            }
            events.add(END);
        }

        Event next() throws InterruptedException {
            Event event = events.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertThat(event).as("an event within %s", WAIT).isNotNull();
            return event;
        }

        /** As closing the tab does: the connection goes away. */
        @Override
        public void close() {
            http.shutdownNow();
        }

    }

}
