package com.thinkxecai.agent;

import com.thinkxecai.model.dto.KnowledgeBaseDTO;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The one system message of a run (ADR 041): the agent's own system prompt, then the decision
 * guidance of the starting design (in English) and the knowledge bases the agent may search. The
 * model needs the knowledge-base ids to call {@code KnowledgeTool}. One message because Spring AI's
 * Gemini model allows only one system message per prompt. Built per run and never stored.
 */
public final class RuntimeInstructions {

    static final String GUIDANCE = """
            You are the decision module of an agent and must complete the user's whole request.

            Principles:
            1. Read the user's original request carefully and make sure every part of it gets done.
            2. Give your final answer only when the whole request is complete.
            3. When information is missing, get it with the available tools before asking the user.

            Decide the next action from the conversation so far. If a tool is needed, call it.""";

    static final String NO_KNOWLEDGE_BASES = "You have no knowledge bases.";

    private RuntimeInstructions() {
    }

    public static SystemMessage systemMessage(String systemPrompt, List<KnowledgeBaseDTO> knowledgeBases) {
        StringBuilder text = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            text.append(systemPrompt.strip()).append("\n\n");
        }
        text.append(GUIDANCE).append("\n\n").append(knowledgeBaseSection(knowledgeBases));
        return new SystemMessage(text.toString());
    }

    private static String knowledgeBaseSection(List<KnowledgeBaseDTO> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return NO_KNOWLEDGE_BASES;
        }
        String list = knowledgeBases.stream()
                .map(kb -> "- " + kb.getId() + ": " + oneLine(kb.getName())
                        + (kb.getDescription() == null || kb.getDescription().isBlank() ? ""
                        : " — " + oneLine(kb.getDescription())))
                .collect(Collectors.joining("\n"));
        return "Knowledge bases available to you (search them with KnowledgeTool, passing the id):\n" + list
                + "\nWhen context is missing, search these knowledge bases first.";
    }

    /** Keeps one entry per line, so a description cannot start what looks like another entry. */
    private static String oneLine(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

}
