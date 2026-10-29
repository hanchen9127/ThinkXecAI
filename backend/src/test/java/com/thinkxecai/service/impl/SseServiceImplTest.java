package com.thinkxecai.service.impl;

import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.sse.SseMessage;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.SseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Best-effort delivery and one stream per session (ISSUE-007, ADR 048), with emitters that record what
 * they are sent and can be made to fail as a gone client or a completed emitter does.
 */
@ExtendWith(OutputCaptureExtension.class)
class SseServiceImplTest {

    private static final UUID SESSION = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");
    private static final String INIT = "event:init\ndata:connected\n\n";
    private static final String REPLACED = "event:replaced\ndata:replaced\n\n";

    /** The emitters connect() gets, in order. */
    private final Deque<FakeEmitter> next = new ArrayDeque<>();
    private final SseServiceImpl service = new SseServiceImpl(next::removeFirst);

    private FakeEmitter connect() {
        return connect(new FakeEmitter());
    }

    private FakeEmitter connect(FakeEmitter emitter) {
        next.add(emitter);
        assertThat(service.connect(SESSION)).isSameAs(emitter);
        return emitter;
    }

    private static SseMessage message(String id) {
        return SseMessage.generated(ChatMessageVO.builder().id(id).sessionId(SESSION.toString())
                .role(ChatMessageRole.ASSISTANT).content("text " + id).build());
    }

    private static String event(SseMessage message) {
        return "event:message\ndata:<application/json " + message + ">\n\n";
    }

    @Test
    void theDefaultEmitterTimesOutAfterThirtyMinutes() {
        assertThat(new SseServiceImpl().connect(SESSION).getTimeout()).isEqualTo(SseService.TIMEOUT.toMillis())
                .isEqualTo(30 * 60 * 1000L);
    }

    @Test
    void aConnectedClientGetsInitThenItsMessagesAsJson() {
        FakeEmitter emitter = connect();
        SseMessage first = message("1");
        SseMessage second = message("2");

        service.send(SESSION, first);
        service.send(SESSION, second);

        assertThat(emitter.sent).containsExactly(INIT, event(first), event(second));
        assertThat(emitter.completed).isFalse();
    }

    @Test
    void sendingWithoutAClientDoesNothing() {
        service.send(SESSION, message("1"));
    }

    @Test
    void aGoneClientIsRemovedAndCompletedWithItsError() {
        FakeEmitter emitter = connect();
        IOException gone = new IOException("Broken pipe");
        emitter.failure = gone;

        service.send(SESSION, message("1"));

        assertThat(emitter.completedWith).isSameAs(gone);
        emitter.failure = null;
        service.send(SESSION, message("2"));
        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void aCompletedEmitterIsRemovedAndCompletedWithItsError() {
        FakeEmitter emitter = connect();
        IllegalStateException completed = new IllegalStateException("ResponseBodyEmitter has already completed");
        emitter.failure = completed;

        service.send(SESSION, message("1"));

        assertThat(emitter.completedWith).isSameAs(completed);
        emitter.failure = null;
        service.send(SESSION, message("2"));
        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void connectingAgainReplacesTheOldStreamWhichEndsAfterAReplacedEvent() {
        FakeEmitter old = connect();
        FakeEmitter current = connect();
        SseMessage message = message("1");

        // the old emitter's completion callback runs only after its successor is registered
        old.completion.run();
        service.send(SESSION, message);

        assertThat(old.sent).containsExactly(INIT, REPLACED);
        assertThat(old.completed).isTrue();
        assertThat(old.completedWith).isNull();
        assertThat(current.sent).containsExactly(INIT, event(message));
        assertThat(current.completed).isFalse();
    }

    @Test
    void aReplacedStreamThatIsAlreadyGoneIsCompletedWithItsError() {
        FakeEmitter old = connect();
        IOException gone = new IOException("Broken pipe");
        old.failure = gone;
        FakeEmitter current = connect();

        assertThat(old.completedWith).isSameAs(gone);
        assertThat(current.sent).containsExactly(INIT);
    }

    @Test
    void theCurrentStreamsTimeoutRemovesItAndEndsItNormally() {
        FakeEmitter emitter = connect();

        emitter.timeout.run();
        service.send(SESSION, message("1"));

        assertThat(emitter.completed).isTrue();
        assertThat(emitter.completedWith).isNull();
        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void theCurrentStreamsCompletionRemovesIt() {
        FakeEmitter emitter = connect();

        emitter.completion.run();
        service.send(SESSION, message("1"));

        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void aFailedSendIsLoggedWithItsCauseButNotWhatWasSent(CapturedOutput output) {
        FakeEmitter emitter = connect();
        emitter.failure = new IllegalStateException("Failed to send text secret",
                new HttpMessageNotWritableException("Could not write JSON: text secret"));

        service.send(SESSION, message("secret"));

        assertThat(output).contains("SSE send failed for session " + SESSION
                + ": IllegalStateException caused by HttpMessageNotWritableException");
        assertThat(output).doesNotContain("secret");
    }

    @Test
    void theCurrentStreamsErrorRemovesIt() {
        FakeEmitter emitter = connect();

        emitter.error.accept(new IOException("Connection reset"));
        service.send(SESSION, message("1"));

        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void aReplacedStreamsTimeoutAndErrorLeaveItsSuccessorRegistered() {
        FakeEmitter old = connect();
        FakeEmitter current = connect();
        SseMessage message = message("1");

        old.timeout.run();
        old.error.accept(new IOException("Connection reset"));
        service.send(SESSION, message);

        assertThat(current.sent).containsExactly(INIT, event(message));
    }

    @Test
    void aFailedInitLeavesTheStreamUnregisteredAndTheOldOneInPlace() {
        FakeEmitter old = connect();
        FakeEmitter failing = new FakeEmitter();
        IllegalStateException completed = new IllegalStateException("ResponseBodyEmitter has already completed");
        failing.failure = completed;
        SseMessage message = message("1");

        connect(failing);
        failing.failure = null;
        service.send(SESSION, message);

        assertThat(failing.completedWith).isSameAs(completed);
        assertThat(failing.sent).isEmpty();
        assertThat(old.sent).containsExactly(INIT, event(message));
        assertThat(old.completed).isFalse();
    }

    @Test
    void closeCompletesAndForgetsTheStream() {
        FakeEmitter emitter = connect();

        service.close(SESSION);
        service.send(SESSION, message("1"));

        assertThat(emitter.completed).isTrue();
        assertThat(emitter.completedWith).isNull();
        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void closingTheCurrentStreamByItsEmitterCompletesAndForgetsIt() {
        FakeEmitter emitter = connect();

        service.close(SESSION, emitter);
        service.send(SESSION, message("1"));

        assertThat(emitter.completed).isTrue();
        assertThat(emitter.sent).containsExactly(INIT);
    }

    @Test
    void closingAReplacedStreamByItsEmitterLeavesItsSuccessor() {
        FakeEmitter old = connect();
        FakeEmitter current = connect();
        SseMessage message = message("1");

        service.close(SESSION, old);
        service.send(SESSION, message);

        assertThat(current.sent).containsExactly(INIT, event(message));
        assertThat(current.completed).isFalse();
    }

    @Test
    void closingWithoutAClientDoesNothing() {
        service.close(SESSION);
    }

    /**
     * Records each event as the text Spring MVC would write, with JSON data shown as {@code <type object>}.
     * Callbacks are kept for the test to run, as Spring MVC would when the client goes or the timeout passes.
     */
    private static class FakeEmitter extends SseEmitter {

        final List<String> sent = Collections.synchronizedList(new ArrayList<>());
        volatile Exception failure;
        volatile boolean completed;
        volatile Throwable completedWith;
        Runnable completion = () -> { };
        Runnable timeout = () -> { };
        Consumer<Throwable> error = e -> { };

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure instanceof IllegalStateException state) {
                throw state;
            }
            sent.add(render(builder.build()));
        }

        private static String render(Set<DataWithMediaType> items) {
            StringBuilder text = new StringBuilder();
            for (DataWithMediaType item : items) {
                text.append(item.getData() instanceof String string
                        ? string : "<" + item.getMediaType() + " " + item.getData() + ">");
            }
            return text.toString();
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            completed = true;
            completedWith = ex;
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeout = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            error = callback;
        }

    }

}
