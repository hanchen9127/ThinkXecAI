package com.thinkxecai.agent;

/**
 * What the user reads when a run cannot finish (ADR 043). Saved as a plain assistant message; the
 * reason is generic on purpose, the details go to the log only.
 */
public final class AgentNotices {

    private static final String COULD_NOT_FINISH = "The agent could not finish this request: ";

    public static final String MODEL_FAILED = COULD_NOT_FINISH + "the model did not respond or returned an error.";
    public static final String AGENT_GONE = COULD_NOT_FINISH + "the agent no longer exists.";
    public static final String MODEL_UNAVAILABLE = COULD_NOT_FINISH + "the agent's model is not available.";
    public static final String BUSY = COULD_NOT_FINISH + "too many requests are being processed. Please try again.";
    public static final String UNEXPECTED = COULD_NOT_FINISH + "an unexpected error occurred.";
    public static final String MAX_STEPS =
            "The agent stopped after " + AgentLimits.MAX_STEPS + " steps without finishing the request.";

    /** A run the user stopped (ADR 060). */
    public static final String STOPPED = "Stopped.";

    private AgentNotices() {
    }

}
