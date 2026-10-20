package com.thinkxecai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs one session's agent runs one at a time, in the order they were submitted; runs of different
 * sessions run in parallel (ADR 042). Each session keeps the tail of a chain of runs; a new run starts
 * on the executor once the tail is done, so no thread blocks while waiting, and the entry is removed
 * when the session has nothing left to run.
 */
@Slf4j
@Component
public class AgentRunScheduler {

    private static final CompletableFuture<Void> IDLE = CompletableFuture.completedFuture(null);

    private final AgentRunner runner;
    private final Executor executor;
    private final TransactionTemplate withoutTransaction;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public AgentRunScheduler(AgentRunner runner, @Qualifier("asyncEventExecutor") Executor executor,
                             PlatformTransactionManager transactionManager) {
        this.runner = runner;
        this.executor = executor;
        this.withoutTransaction = new TransactionTemplate(transactionManager);
        this.withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /**
     * Queues the run that answers {@code messageId} behind the session's earlier runs. Never throws: the
     * caller is a commit callback.
     */
    public void submit(UUID agentId, UUID sessionId, UUID messageId) {
        // The session's turn ends only after a rejected run's notice is saved, so the next run sees it.
        CompletableFuture<Void> turn = new CompletableFuture<>();
        CompletableFuture<Void> previous = tails.put(sessionId, turn);
        (previous == null ? IDLE : previous)
                .thenRunAsync(() -> runner.run(agentId, sessionId, messageId), executor)
                .whenComplete((ignored, failure) -> {
                    try {
                        if (failure != null) {
                            failed(agentId, sessionId, failure);
                        }
                    } finally {
                        tails.remove(sessionId, turn);
                        turn.complete(null);
                    }
                });
    }

    private void failed(UUID agentId, UUID sessionId, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (!(cause instanceof RejectedExecutionException)) {
            // AgentRunner.run catches every exception; only an Error gets here.
            log.error("Agent {} in session {} ended with an error", agentId, sessionId, cause);
            return;
        }
        log.warn("Agent {} not run for session {}: the executor is full", agentId, sessionId);
        // A rejection seen on the request thread arrives in the commit callback, while the committed
        // transaction is still bound to the thread. Suspended, the save runs in a transaction of its own
        // that it commits or rolls back by itself (a joined one would turn an expected failure, such as a
        // deleted session, into an UnexpectedRollbackException).
        try {
            withoutTransaction.executeWithoutResult(status -> runner.notify(sessionId, AgentNotices.BUSY));
        } catch (RuntimeException e) {
            log.error("Notice not saved for session {}", sessionId, e);
        }
    }

    /** Sessions with a run queued or running; for tests. */
    int activeSessions() {
        return tails.size();
    }

}
