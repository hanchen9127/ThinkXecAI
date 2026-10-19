package com.thinkxecai.event;

import com.thinkxecai.agent.AgentRunScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts an agent run once the user message is committed, so the run can read it and a rolled-back
 * message starts nothing (ISSUE-004). It only hands the run to the scheduler, which runs it on
 * {@code asyncEventExecutor}; without {@code @Async}, a full executor reaches the scheduler and becomes
 * a notice instead of an exception lost in the commit callback.
 */
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final AgentRunScheduler scheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        scheduler.submit(event.agentId(), event.sessionId());
    }

}
