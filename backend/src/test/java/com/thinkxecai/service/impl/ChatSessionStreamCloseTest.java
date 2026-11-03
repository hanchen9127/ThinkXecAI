package com.thinkxecai.service.impl;

import com.thinkxecai.agent.AgentRunScheduler;
import com.thinkxecai.converter.ChatSessionConverter;
import com.thinkxecai.exception.BizException;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.ChatSessionMapper;
import com.thinkxecai.service.SseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A deleted session's stream is closed once the delete has committed, never before and never after a
 * rollback (ADR 049). The transaction's commit and rollback are driven by hand.
 */
class ChatSessionStreamCloseTest {

    private static final UUID SESSION = UUID.fromString("3c9e1a7b-2d4f-4a6b-8c0d-1e2f3a4b5c6d");

    private final ChatSessionMapper mapper = mock(ChatSessionMapper.class);
    private final SseService sseService = mock(SseService.class);
    private final ChatSessionFacadeServiceImpl service =
            new ChatSessionFacadeServiceImpl(mapper, mock(AgentMapper.class), new ChatSessionConverter(), sseService,
                    mock(AgentRunScheduler.class));

    @BeforeEach
    void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void theStreamIsClosedOnceTheDeleteHasCommitted() {
        when(mapper.deleteById(SESSION)).thenReturn(1);

        service.deleteChatSession(SESSION);

        verifyNoInteractions(sseService);
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(sseService).close(SESSION);
    }

    @Test
    void aRolledBackDeleteLeavesTheStreamOpen() {
        when(mapper.deleteById(SESSION)).thenReturn(1);

        service.deleteChatSession(SESSION);
        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(sseService);
    }

    @Test
    void anUnknownSessionLeavesNothingToDoAfterTheTransaction() {
        assertThatThrownBy(() -> service.deleteChatSession(SESSION)).isInstanceOf(BizException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(sseService);
    }

}
