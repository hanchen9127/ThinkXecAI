package com.thinkxecai.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Per-session ordering of runs (ADR 042) with a mocked runner on a real thread pool. */
class AgentRunSchedulerTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final AgentRunner runner = mock(AgentRunner.class);
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final UUID session = UUID.randomUUID();
    private final UUID message = UUID.randomUUID();

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private AgentRunScheduler scheduler(Executor executor) {
        return new AgentRunScheduler(runner, executor, mock(PlatformTransactionManager.class));
    }

    /** Runs of {@code agent} in the session record their start and end; they finish only once the returned latch is released. */
    private CountDownLatch blocking(UUID agent, String name) {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            events.add("start " + name);
            assertThat(release.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            events.add("end " + name);
            return null;
        }).when(runner).run(agent, session, message);
        return release;
    }

    private void recording(UUID agent, UUID sessionId, String name) {
        doAnswer(invocation -> {
            events.add("run " + name);
            return null;
        }).when(runner).run(agent, sessionId, message);
    }

    @Test
    void runsOfOneSessionTakeTurnsInTheOrderSubmitted() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        CountDownLatch release = blocking(first, "first");
        recording(second, session, "second");
        recording(third, session, "third");
        AgentRunScheduler scheduler = scheduler(pool);

        scheduler.submit(first, session, message);
        await().atMost(WAIT).until(() -> events.contains("start first"));
        scheduler.submit(second, session, message);
        scheduler.submit(third, session, message);

        verify(runner, after(300).never()).run(second, session, message);
        release.countDown();
        verify(runner, timeout(WAIT.toMillis())).run(third, session, message);
        assertThat(events).containsExactly("start first", "end first", "run second", "run third");
    }

    @Test
    void runsOfDifferentSessionsOverlap() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        CountDownLatch release = blocking(first, "first");
        recording(second, other, "other");
        AgentRunScheduler scheduler = scheduler(pool);

        scheduler.submit(first, session, message);
        await().atMost(WAIT).until(() -> events.contains("start first"));
        scheduler.submit(second, other, message);

        await().atMost(WAIT).until(() -> events.contains("run other"));
        assertThat(events).doesNotContain("end first");
        release.countDown();
    }

    @Test
    void aRejectedRunLeavesANotice() {
        AgentRunScheduler scheduler = scheduler(task -> {
            throw new TaskRejectedException("queue full");
        });

        scheduler.submit(UUID.randomUUID(), session, message);

        verify(runner).notify(session, AgentNotices.BUSY);
        verify(runner, never()).run(any(), any(), any());
        assertThat(scheduler.activeSessions()).isZero();
    }

    @Test
    void aRunRejectedWhileWaitingLeavesANoticeBeforeTheNextRunStarts() {
        UUID first = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        CountDownLatch release = blocking(first, "first");
        recording(third, session, "third");
        doAnswer(invocation -> {
            Thread.sleep(300); // a slow save: the next run must still wait for it
            return events.add("notice");
        }).when(runner).notify(session, AgentNotices.BUSY);
        AtomicInteger submissions = new AtomicInteger();
        AgentRunScheduler scheduler = scheduler(task -> {
            if (submissions.incrementAndGet() == 2) {
                throw new TaskRejectedException("queue full");
            }
            pool.execute(task);
        });

        scheduler.submit(first, session, message);
        await().atMost(WAIT).until(() -> events.contains("start first"));
        scheduler.submit(UUID.randomUUID(), session, message);
        scheduler.submit(third, session, message);
        release.countDown();

        verify(runner, timeout(WAIT.toMillis())).run(third, session, message);
        assertThat(events).containsExactly("start first", "end first", "notice", "run third");
    }

    @Test
    void aRunThatThrowsDoesNotHoldUpTheSession() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        doThrow(new Error("boom")).when(runner).run(first, session, message);
        AgentRunScheduler scheduler = scheduler(pool);

        scheduler.submit(first, session, message);
        scheduler.submit(second, session, message);

        verify(runner, timeout(WAIT.toMillis())).run(second, session, message);
        verify(runner, never()).notify(any(), any());
    }

    @Test
    void theSessionIsForgottenOnceItsRunsAreDone() {
        AgentRunScheduler scheduler = scheduler(pool);

        scheduler.submit(UUID.randomUUID(), session, message);
        scheduler.submit(UUID.randomUUID(), session, message);
        scheduler.submit(UUID.randomUUID(), UUID.randomUUID(), message);

        await().atMost(WAIT).until(() -> scheduler.activeSessions() == 0);
        verify(runner, timeout(WAIT.toMillis()).times(3)).run(any(), any(), any());
    }

}
