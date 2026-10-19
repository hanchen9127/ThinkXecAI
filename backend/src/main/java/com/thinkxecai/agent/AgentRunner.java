package com.thinkxecai.agent;

import com.thinkxecai.exception.ChatSessionGoneException;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.GeneratedMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs an agent for a session once and makes sure the session learns how it ended: a run that cannot
 * finish saves a notice with a generic reason (ADR 043). Never throws, since nobody waits for a run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunner {

    private final AgentRuntimeFactory agentRuntimeFactory;
    private final GeneratedMessageService generatedMessageService;
    private final GeneratedMessagePublisher publisher;

    public void run(UUID agentId, UUID sessionId) {
        log.info("Agent {} runs for session {}", agentId, sessionId);
        try {
            AgentRuntime.Outcome outcome = agentRuntimeFactory.create(agentId, sessionId).run();
            log.info("Agent {} in session {} ended: {}", agentId, sessionId, outcome);
            if (outcome == AgentRuntime.Outcome.MAX_STEPS) {
                notify(sessionId, AgentNotices.MAX_STEPS);
            }
        } catch (ChatSessionGoneException e) {
            log.info("Agent {} stopped: session {} was deleted", agentId, sessionId);
        } catch (AgentRunFailure e) {
            log.warn("Agent {} in session {} failed: {}", agentId, sessionId, e.getMessage(), e);
            notify(sessionId, e.notice());
        } catch (RuntimeException e) {
            log.error("Agent {} in session {} failed unexpectedly", agentId, sessionId, e);
            notify(sessionId, AgentNotices.UNEXPECTED);
        }
    }

    /** Saves and delivers a notice; if even that fails there is nothing left to tell, so it is logged. */
    public void notify(UUID sessionId, String notice) {
        try {
            for (ChatMessageVO saved : generatedMessageService.save(sessionId, List.of(GeneratedMessages.notice(notice)))) {
                try {
                    publisher.publish(sessionId, saved);
                } catch (RuntimeException e) {
                    log.warn("Delivering notice {} of session {} failed", saved.getId(), sessionId, e);
                }
            }
        } catch (ChatSessionGoneException e) {
            log.info("Notice not saved: session {} was deleted", sessionId);
        } catch (RuntimeException e) {
            log.error("Notice not saved for session {}", sessionId, e);
        }
    }

}
