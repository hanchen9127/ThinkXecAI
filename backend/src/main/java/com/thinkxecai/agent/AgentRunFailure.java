package com.thinkxecai.agent;

/** A run that cannot go on for a known reason; {@link #notice()} is what the session is told (ADR 043). */
public class AgentRunFailure extends RuntimeException {

    private final String notice;

    public AgentRunFailure(String notice, String message, Throwable cause) {
        super(message, cause);
        this.notice = notice;
    }

    public String notice() {
        return notice;
    }

}
