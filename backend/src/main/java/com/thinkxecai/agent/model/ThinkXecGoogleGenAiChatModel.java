package com.thinkxecai.agent.model;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini chat model that keeps the function calls of a response that also carries text. Spring AI
 * 1.1.x treats a candidate as a tool call only when all of its parts are function calls, so text plus
 * a call loses the call and the agent stops early (spring-ai #5466); it also turns a text answer split
 * into several parts into several generations, of which callers read only the first.
 * <p>
 * Here every candidate becomes one assistant message, as with DeepSeek (ADR 038): the text parts that
 * are not thoughts, joined; every function call, in order; and the thought signatures that Spring AI
 * sends back attached to those calls. Remove this class once a Spring AI release used by this project
 * contains the upstream fix ({@code 775ebaa}); that fix returns one generation per text part plus one
 * for the calls, so the agent runtime must then merge them.
 */
public class ThinkXecGoogleGenAiChatModel extends GoogleGenAiChatModel {

    /** Metadata keys read back by Spring AI ({@code thoughtSignatures}) or kept from its own conversion. */
    static final String THOUGHT_SIGNATURES = "thoughtSignatures";
    static final String CANDIDATE_INDEX = "candidateIndex";
    static final String FINISH_REASON = "finishReason";

    public ThinkXecGoogleGenAiChatModel(Client genAiClient, GoogleGenAiChatOptions defaultOptions,
                                        ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
                                        ObservationRegistry observationRegistry,
                                        ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
        super(genAiClient, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
                toolExecutionEligibilityPredicate);
    }

    @Override
    protected List<Generation> responseCandidateToGeneration(Candidate candidate) {
        FinishReason finishReason = candidate.finishReason().orElse(new FinishReason(FinishReason.Known.STOP));
        List<Part> parts = candidate.content().flatMap(Content::parts).orElse(List.of());

        String text = parts.stream()
                .filter(part -> !part.thought().orElse(false))
                .map(part -> part.text().orElse(""))
                .collect(Collectors.joining());
        List<Part> callParts = parts.stream().filter(part -> part.functionCall().isPresent()).toList();
        List<AssistantMessage.ToolCall> toolCalls = callParts.stream()
                .map(part -> toToolCall(part.functionCall().get()))
                .toList();

        // Spring AI attaches the stored signatures to the function calls one by one when it sends this
        // message back, so with calls only theirs are kept, in order (Gemini signs the first call).
        List<byte[]> thoughtSignatures = (callParts.isEmpty() ? parts : callParts).stream()
                .flatMap(part -> part.thoughtSignature().stream())
                .toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(CANDIDATE_INDEX, candidate.index().orElse(0));
        metadata.put(FINISH_REASON, finishReason);
        if (!thoughtSignatures.isEmpty()) {
            metadata.put(THOUGHT_SIGNATURES, thoughtSignatures);
        }

        AssistantMessage message = AssistantMessage.builder()
                .content(text)
                .properties(metadata)
                .toolCalls(toolCalls)
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason.toString())
                .build();
        return List.of(new Generation(message, generationMetadata));
    }

    private static AssistantMessage.ToolCall toToolCall(FunctionCall call) {
        return new AssistantMessage.ToolCall(call.id().orElse(""), "function", call.name().orElse(""),
                ModelOptionsUtils.toJsonString(call.args().orElse(Map.of())));
    }

}
