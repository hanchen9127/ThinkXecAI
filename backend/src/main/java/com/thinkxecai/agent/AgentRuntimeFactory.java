package com.thinkxecai.agent;

import com.thinkxecai.agent.memory.AgentMemoryWindow;
import com.thinkxecai.agent.memory.MemoryMessages;
import com.thinkxecai.agent.memory.MemorySanitizer;
import com.thinkxecai.agent.model.ChatClientRegistry;
import com.thinkxecai.agent.tool.KnowledgeTool;
import com.thinkxecai.converter.AgentConverter;
import com.thinkxecai.converter.ChatMessageConverter;
import com.thinkxecai.converter.KnowledgeBaseConverter;
import com.thinkxecai.mapper.AgentMapper;
import com.thinkxecai.mapper.ChatMessageMapper;
import com.thinkxecai.mapper.KnowledgeBaseMapper;
import com.thinkxecai.model.dto.AgentDTO;
import com.thinkxecai.model.dto.ChatMessageDTO;
import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import com.thinkxecai.model.entity.AgentEntity;
import com.thinkxecai.service.GeneratedMessageService;
import com.thinkxecai.service.ToolFacadeService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
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
                .build();
    }

    /** @throws AgentRunFailure if the agent no longer exists or its model is not registered */
    public AgentRuntime create(UUID agentId, UUID sessionId) {
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
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolFacadeService.toolCallbacksFor(agent.getAllowedTools()))
                // Always set, also when empty: Spring AI refuses to call KnowledgeTool without it (ADR 029).
                .toolContext(Map.of(KnowledgeTool.ALLOWED_KB_IDS,
                        knowledgeBases.stream().map(kb -> kb.getId().toString()).toList()))
                // Request options replace the client's defaults, so this is set again here (ADR 040).
                .internalToolExecutionEnabled(false)
                .build();

        int messageLength = agent.getChatOptions().getMessageLength();
        List<ChatMessageDTO> stored = chatMessageMapper.selectRecentBySessionId(sessionId, messageLength).stream()
                .map(chatMessageConverter::toDTO)
                .toList();
        SystemMessage systemMessage = RuntimeInstructions.systemMessage(agent.getSystemPrompt(), knowledgeBases);
        ChatMemory memory = AgentMemoryWindow.create(sessionId.toString(), systemMessage,
                MemoryMessages.toSpringAi(MemorySanitizer.sanitize(stored)), messageLength);

        return new AgentRuntime(agentId, sessionId, chatClient, memory, options, toolCallingManager,
                generatedMessageService, publisher);
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
