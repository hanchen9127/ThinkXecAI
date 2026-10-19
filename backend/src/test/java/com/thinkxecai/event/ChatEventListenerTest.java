package com.thinkxecai.event;

import com.thinkxecai.agent.AgentRunScheduler;
import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** When a chat event starts a run (ISSUE-004): only after its transaction commits, or at once without one. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatEventListenerTest extends ServiceTestSupport {

    private final ChatEvent event = new ChatEvent(UUID.randomUUID(), UUID.randomUUID());

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AgentRunScheduler scheduler;

    @Test
    void anEventInATransactionThatRollsBackStartsNoRun() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(scheduler, never()).submit(any(), any());
    }

    @Test
    void anEventStartsARunOnlyOnceItsTransactionCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            verify(scheduler, never()).submit(any(), any());
        });

        verify(scheduler).submit(event.agentId(), event.sessionId());
    }

    @Test
    void anEventOutsideATransactionStartsARunAtOnce() {
        eventPublisher.publishEvent(event);

        verify(scheduler).submit(event.agentId(), event.sessionId());
    }

}
