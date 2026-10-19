package com.thinkxecai.agent;

import com.thinkxecai.agent.tool.TerminateTool;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.vo.ChatMessageVO;
import com.thinkxecai.service.GeneratedMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;

import java.util.List;
import java.util.UUID;

/**
 * One agent run: the Think-Execute loop over one session's memory. Think: call the model with the
 * memory and save its reply. Execute: run the tools it asked for and save their results. It ends when
 * a reply asks for no tool, when {@code terminate} was called, or after {@link AgentLimits#MAX_STEPS}
 * steps. Spring AI never runs tools itself here (ADR 040): the loop does, so every step is saved.
 * <p>
 * Each saved message is handed to the {@link GeneratedMessagePublisher}; whatever delivery does, the
 * run goes on (ISSUE-007). Built by {@link AgentRuntimeFactory}, run once by {@link AgentRunner}.
 */
@Slf4j
public class AgentRuntime {

    /** How a run that did not fail ended. */
    public enum Outcome { ANSWERED, TERMINATED, MAX_STEPS }

    private final UUID agentId;
    private final UUID sessionId;
    private final ChatClient chatClient;
    private final ChatMemory memory;
    private final ToolCallingChatOptions options;
    private final ToolCallingManager toolCallingManager;
    private final GeneratedMessageService generatedMessageService;
    private final GeneratedMessagePublisher publisher;
    private AgentState state = AgentState.IDLE;

    AgentRuntime(UUID agentId, UUID sessionId, ChatClient chatClient, ChatMemory memory,
                 ToolCallingChatOptions options, ToolCallingManager toolCallingManager,
                 GeneratedMessageService generatedMessageService, GeneratedMessagePublisher publisher) {
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.chatClient = chatClient;
        this.memory = memory;
        this.options = options;
        this.toolCallingManager = toolCallingManager;
        this.generatedMessageService = generatedMessageService;
        this.publisher = publisher;
    }

    public AgentState state() {
        return state;
    }

    /**
     * @throws AgentRunFailure if the model call fails; any other exception is unexpected. The state is
     *                         {@code ERROR} afterwards.
     */
    public Outcome run() {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException("An agent runtime runs once");
        }
        try {
            for (int step = 1; step <= AgentLimits.MAX_STEPS; step++) {
                moveTo(AgentState.THINKING, step);
                AssistantMessage reply = think();
                if (!reply.hasToolCalls()) {
                    moveTo(AgentState.FINISHED, step);
                    return Outcome.ANSWERED;
                }
                moveTo(AgentState.EXECUTING, step);
                if (execute(reply)) {
                    moveTo(AgentState.FINISHED, step);
                    return Outcome.TERMINATED;
                }
            }
            moveTo(AgentState.FINISHED, AgentLimits.MAX_STEPS);
            return Outcome.MAX_STEPS;
        } catch (RuntimeException e) {
            state = AgentState.ERROR;
            throw e;
        }
    }

    /** Calls the model and saves its reply, with every tool call given an id first. */
    private AssistantMessage think() {
        Prompt prompt = new Prompt(conversation(), options);
        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (RuntimeException e) {
            throw new AgentRunFailure(AgentNotices.MODEL_FAILED, "Model call failed", e);
        }
        if (response == null || response.getResult() == null) {
            throw new AgentRunFailure(AgentNotices.MODEL_FAILED, "Model returned no reply", null);
        }
        AssistantMessage reply = ToolCallIds.fill(response.getResult().getOutput());
        save(List.of(GeneratedMessages.assistant(reply)));
        memory.add(conversationId(), reply);
        return reply;
    }

    /** Runs the reply's tool calls and saves their results; true if {@code terminate} was called. */
    private boolean execute(AssistantMessage reply) {
        // The prompt carries the tool callbacks and the tool context (the agent's knowledge bases).
        Prompt prompt = new Prompt(conversation(), options);
        ChatResponse calls = new ChatResponse(List.of(new Generation(reply)));
        List<Message> history = toolCallingManager.executeToolCalls(prompt, calls).conversationHistory();
        ToolResponseMessage results = (ToolResponseMessage) history.get(history.size() - 1);
        save(GeneratedMessages.toolResults(results));
        memory.add(conversationId(), results);
        return results.getResponses().stream().anyMatch(response -> TerminateTool.NAME.equals(response.name()));
    }

    private List<Message> conversation() {
        return memory.get(conversationId());
    }

    private String conversationId() {
        return sessionId.toString();
    }

    private void save(List<ChatMessageDTO> messages) {
        for (ChatMessageVO saved : generatedMessageService.save(sessionId, messages)) {
            try {
                publisher.publish(sessionId, saved);
            } catch (RuntimeException e) {
                log.warn("Delivering message {} of session {} failed; the run goes on", saved.getId(), sessionId, e);
            }
        }
    }

    private void moveTo(AgentState next, int step) {
        log.debug("Agent {} in session {}: step {} {} -> {}", agentId, sessionId, step, state, next);
        state = next;
    }

}
