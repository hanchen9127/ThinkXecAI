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
import java.util.function.BooleanSupplier;

/**
 * One agent run: the Think-Execute loop over one session's memory. Think: call the model with the
 * memory and save its reply. Execute: run the tools it asked for and save their results. It ends when
 * a reply asks for no tool, when {@code terminate} was called, after {@link AgentLimits#MAX_STEPS}
 * steps, or when a stop was requested (checked before each Think and each Execute, so a model call or
 * tool already running finishes first; ADR 060). Spring AI never runs tools itself here (ADR 040): the loop does, so every step is saved.
 * <p>
 * Each saved message is handed to the {@link GeneratedMessagePublisher}; whatever delivery does, the
 * run goes on (ISSUE-007). Built by {@link AgentRuntimeFactory}, run once by {@link AgentRunner}.
 */
@Slf4j
public class AgentRuntime {

    /** How a run that did not fail ended. */
    public enum Outcome { ANSWERED, TERMINATED, MAX_STEPS, STOPPED }

    /** No tools: both models then answer a history with tool calls in text (checked against the real APIs). */
    private static final ToolCallingChatOptions ANSWER_ONLY = ToolCallingChatOptions.builder()
            .internalToolExecutionEnabled(false)
            .build();

    private final UUID agentId;
    private final UUID sessionId;
    private final ChatClient chatClient;
    private final ChatMemory memory;
    private final ToolCallingChatOptions options;
    private final ToolCallingManager toolCallingManager;
    private final GeneratedMessageService generatedMessageService;
    private final GeneratedMessagePublisher publisher;
    private AgentState state = AgentState.IDLE;
    /** Set when {@code terminate} came without text: the next call offers no tools, so its reply is text (ADR 045). */
    private boolean answerOnly;

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
     * @param stopRequested asked before each Think and each Execute; true ends the run with {@code STOPPED}
     * @throws AgentRunFailure if the model call fails; any other exception is unexpected. The state is
     *                         {@code ERROR} afterwards.
     */
    public Outcome run(BooleanSupplier stopRequested) {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException("An agent runtime runs once");
        }
        try {
            for (int step = 1; step <= AgentLimits.MAX_STEPS; step++) {
                if (stopRequested.getAsBoolean()) {
                    return stopped(step);
                }
                moveTo(AgentState.THINKING, step);
                AssistantMessage reply = think();
                if (!reply.hasToolCalls()) {
                    moveTo(AgentState.FINISHED, step);
                    return Outcome.ANSWERED;
                }
                // The saved reply keeps its calls; the next run's memory sanitizing drops calls without results.
                if (stopRequested.getAsBoolean()) {
                    return stopped(step);
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

    private Outcome stopped(int step) {
        log.info("Agent {} in session {} stopped before step {} went on", agentId, sessionId, step);
        moveTo(AgentState.FINISHED, step);
        return Outcome.STOPPED;
    }

    /** Calls the model and saves its reply, with every tool call given an id first. */
    private AssistantMessage think() {
        Prompt prompt = new Prompt(conversation(), answerOnly ? ANSWER_ONLY : options);
        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (RuntimeException e) {
            throw new AgentRunFailure(AgentNotices.MODEL_FAILED, "Model call failed", e);
        }
        if (response == null || response.getResult() == null) {
            throw new AgentRunFailure(AgentNotices.MODEL_FAILED, "Model returned no reply", null);
        }
        AssistantMessage output = response.getResult().getOutput();
        // Saved, an empty reply would answer nothing and become a message Gemini refuses (no parts).
        if (!output.hasToolCalls() && (output.getText() == null || output.getText().isBlank())) {
            throw new AgentRunFailure(AgentNotices.MODEL_FAILED, "Model returned an empty reply", null);
        }
        AssistantMessage reply = ToolCallIds.fill(output);
        save(List.of(GeneratedMessages.assistant(reply)));
        memory.add(conversationId(), reply);
        return reply;
    }

    /**
     * Runs the reply's tool calls and saves their results; true if {@code terminate} ends the run. It does only
     * in a reply with text: the user sees no tool results, so a run ended without text would answer nothing.
     * Called alone, it is answered with {@link TerminateTool#NOT_ENDED} and the next call offers no tools: Gemini
     * was seen calling it without text again after that result.
     */
    private boolean execute(AssistantMessage reply) {
        // The prompt carries the tool callbacks and the tool context (the agent's knowledge bases).
        Prompt prompt = new Prompt(conversation(), options);
        ChatResponse calls = new ChatResponse(List.of(new Generation(reply)));
        List<Message> history = toolCallingManager.executeToolCalls(prompt, calls).conversationHistory();
        boolean answered = reply.getText() != null && !reply.getText().isBlank();
        ToolResponseMessage results = asJson((ToolResponseMessage) history.get(history.size() - 1), answered);
        save(GeneratedMessages.toolResults(results));
        memory.add(conversationId(), results);
        boolean terminate = results.getResponses().stream().anyMatch(response -> TerminateTool.NAME.equals(response.name()));
        answerOnly = terminate && !answered;
        return terminate && answered;
    }

    /**
     * Error texts and empty results become JSON strings, as the models need them (see {@link ToolJson});
     * {@code terminate} in a reply without text gets {@link TerminateTool#NOT_ENDED}.
     */
    private static ToolResponseMessage asJson(ToolResponseMessage results, boolean answered) {
        return ToolResponseMessage.builder()
                .responses(results.getResponses().stream()
                        .map(response -> new ToolResponseMessage.ToolResponse(
                                response.id(), response.name(), ToolJson.result(
                                        !answered && TerminateTool.NAME.equals(response.name())
                                                ? TerminateTool.NOT_ENDED : response.responseData())))
                        .toList())
                .metadata(results.getMetadata())
                .build();
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
