package com.thinkxecai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.thinkxecai.agent.memory.AgentMemoryWindow;
import com.thinkxecai.agent.memory.MemoryMessages;
import com.thinkxecai.agent.memory.MemorySanitizer;
import com.thinkxecai.agent.memory.RunTurn;
import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.tool.KnowledgeTool;
import com.thinkxecai.agent.tool.ToolCallFailure;
import com.thinkxecai.converter.AgentConverter;
import com.thinkxecai.converter.ChatMessageConverter;
import com.thinkxecai.converter.KnowledgeBaseConverter;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.ChatMessageMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.ChatMessageRole;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import com.thinkxecai.model.entity.AgentEntity;
import com.thinkxecai.model.entity.ChatMessageEntity;
import com.thinkxecai.service.GeneratedMessageService;
import com.thinkxecai.service.ToolFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the runtime for one run from the agent and session as stored now: the agent is read again
 * (it may have been deleted after the message was saved), its model's client, the knowledge bases that
 * still exist, its tools, the one system message, and the session's sanitized recent memory.
 */
@Slf4j
@Component
public class AgentRuntimeFactory {

    private final AgentMapper agentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentConverter agentConverter;
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final ChatMessageConverter chatMessageConverter;
    private final ChatClientRegistry chatClientRegistry;
    private final ToolFacadeService toolFacadeService;
    private final GeneratedMessageService generatedMessageService;
    private final GeneratedMessagePublisher publisher;
    private final ToolCallingManager toolCallingManager;

    public AgentRuntimeFactory(AgentMapper agentMapper, KnowledgeBaseMapper knowledgeBaseMapper,
                               ChatMessageMapper chatMessageMapper, AgentConverter agentConverter,
                               KnowledgeBaseConverter knowledgeBaseConverter, ChatMessageConverter chatMessageConverter,
                               ChatClientRegistry chatClientRegistry, ToolFacadeService toolFacadeService,
                               GeneratedMessageService generatedMessageService, GeneratedMessagePublisher publisher) {
        this.agentMapper = agentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.agentConverter = agentConverter;
        this.knowledgeBaseConverter = knowledgeBaseConverter;
        this.chatMessageConverter = chatMessageConverter;
        this.chatClientRegistry = chatClientRegistry;
        this.toolFacadeService = toolFacadeService;
        this.generatedMessageService = generatedMessageService;
        this.publisher = publisher;
        // Tools come only from the run's options. Spring AI's default would look a name the model made up
        // in the application context and fail the run when it is missing; here the model is told instead.
        this.toolCallingManager = ToolCallingManager.builder()
                .toolCallbackResolver(AgentRuntimeFactory::unknownTool)
                // Spring AI's default rethrows a failure without a runtime cause, such as arguments that are not
                // JSON, and that ends the run; here every tool failure is told to the model instead (ADR 041).
                .toolExecutionExceptionProcessor(AgentRuntimeFactory::toolFailed)
                .build();
    }

    /**
     * @param triggerId the user message this run answers (ADR 046); null keeps the stored order
     * @throws AgentRunFailure if the agent no longer exists or its model is not registered
     */
    public AgentRuntime create(UUID agentId, UUID sessionId, UUID triggerId) {
        AgentEntity entity = agentMapper.selectById(agentId);
        if (entity == null) {
            throw new AgentRunFailure(AgentNotices.AGENT_GONE, "Agent not found: " + agentId, null);
        }
        AgentDTO agent = agentConverter.toDTO(entity);
        ChatClient chatClient;
        try {
            chatClient = chatClientRegistry.get(agent.getModel());
        } catch (IllegalArgumentException e) {
            throw new AgentRunFailure(AgentNotices.MODEL_UNAVAILABLE, e.getMessage(), e);
        }

        List<KnowledgeBaseDTO> knowledgeBases = knowledgeBases(agent.getAllowedKbs());
        // Each call records why it failed, if it did, to be stored with its result (ADR 064).
        ToolOutcomes outcomes = new ToolOutcomes(toolFacadeService.toolCallbacksFor(agent.getAllowedTools()));
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(outcomes.callbacks())
                // Always set, also when empty: Spring AI refuses to call KnowledgeTool without it (ADR 029).
                .toolContext(Map.of(KnowledgeTool.ALLOWED_KB_IDS,
                        knowledgeBases.stream().map(kb -> kb.getId().toString()).toList()))
                // Request options replace the client's defaults, so this is set again here (ADR 040).
                .internalToolExecutionEnabled(false)
                .build();

        int messageLength = agent.getChatOptions().getMessageLength();
        SystemMessage systemMessage = RuntimeInstructions.systemMessage(agent.getSystemPrompt(), knowledgeBases);
        ChatMemory memory = AgentMemoryWindow.create(sessionId.toString(), systemMessage,
                MemoryMessages.toSpringAi(MemorySanitizer.sanitize(storedTurn(sessionId, triggerId, messageLength))),
                messageLength);

        return new AgentRuntime(agentId, sessionId, chatClient, memory, options, toolCallingManager, outcomes,
                generatedMessageService, publisher);
    }

    /**
     * The stored rows the run remembers, its own message last (ADR 046). A trigger that is gone (deleted
     * meanwhile) or is not a user message of this session leaves the latest rows in their stored order.
     */
    private List<ChatMessageDTO> storedTurn(UUID sessionId, UUID triggerId, int messageLength) {
        if (triggerId != null) {
            // after the trigger: about one earlier run's full output, so queued runs cannot grow memory without bound
            int laterLimit = messageLength + 2 * AgentLimits.MAX_STEPS + 1;
            List<ChatMessageDTO> turn = toDTOs(
                    chatMessageMapper.selectTurnBySessionId(sessionId, triggerId, messageLength, laterLimit));
            ChatMessageDTO trigger = turn.stream()
                    .filter(message -> triggerId.equals(message.getId()) && message.getRole() == ChatMessageRole.USER)
                    .findFirst()
                    .orElse(null);
            if (trigger != null) {
                return RunTurn.endWith(turn, trigger);
            }
        }
        return toDTOs(chatMessageMapper.selectRecentBySessionId(sessionId, messageLength));
    }

    private List<ChatMessageDTO> toDTOs(List<ChatMessageEntity> entities) {
        return entities.stream().map(chatMessageConverter::toDTO).toList();
    }

    /** The agent's knowledge bases that still exist, in the agent's order. */
    private List<KnowledgeBaseDTO> knowledgeBases(List<String> allowedKbs) {
        if (allowedKbs == null || allowedKbs.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = allowedKbs.stream().map(UUID::fromString).toList();
        Map<UUID, KnowledgeBaseDTO> found = knowledgeBaseMapper.selectByIds(ids.toArray(UUID[]::new)).stream()
                .map(knowledgeBaseConverter::toDTO)
                .collect(Collectors.toMap(KnowledgeBaseDTO::getId, Function.identity()));
        return ids.stream().filter(found::containsKey).map(found::get).toList();
    }

    /**
     * A failed tool call: the model gets the reason and can correct itself. A tool's own {@link ToolCallFailure}
     * is passed on as it is: its text was written for the model (ADR 064). An {@link Error} still ends the run.
     */
    static String toolFailed(ToolExecutionException exception) {
        String name = exception.getToolDefinition().name();
        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof ToolCallFailure failure) {
            log.debug("Tool {} did not succeed: {}", name, failure.kind());
            return failure.getMessage();
        }
        // The message can quote the model's arguments: only the type at warn level.
        log.warn("Tool {} failed: {}", name, cause.getClass().getSimpleName());
        log.debug("Tool {} failure", name, exception);
        String reason = cause instanceof JsonProcessingException
                ? "the arguments are not valid JSON for this tool"
                : cause.getClass().getSimpleName();
        return "Tool " + name + " failed: " + reason + ".";
    }

    /** A tool the agent does not have: the model gets an error text and can correct itself. */
    private static ToolCallback unknownTool(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("Not available to this agent.")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "Unknown tool: " + name + ". Use only the tools you were given.";
            }
        };
    }

}
