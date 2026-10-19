package com.thinkxecai.event;

import java.util.UUID;

/**
 * Published when a user message is created through the public create-message path; starts an agent
 * run once the transaction commits ({@link ChatEventListener}). The agent is resolved from the session on the server, and
 * the message content is not carried: the runtime reads it from the database with the rest of the memory.
 */
public record ChatEvent(UUID agentId, UUID sessionId) {
}
