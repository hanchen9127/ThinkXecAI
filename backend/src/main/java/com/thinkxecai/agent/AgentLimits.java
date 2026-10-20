package com.thinkxecai.agent;

/** Fixed limits of an agent run. */
public final class AgentLimits {

    /** Think-Execute steps per run; each step adds at most one assistant and one tool-result message. */
    public static final int MAX_STEPS = 20;

    private AgentLimits() {
    }

}
