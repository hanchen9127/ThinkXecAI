package com.thinkxecai.model.request;

/**
 * Maximum sizes of request fields (ADR 032), in characters for text and entries for lists. They keep
 * oversized input out of the database and out of the prompts later sent to paid models.
 */
public final class RequestLimits {

    /** Agent, knowledge-base, and document names, chat-session titles, and uploaded file names. */
    public static final int NAME = 200;
    /** Agent and knowledge-base descriptions. */
    public static final int DESCRIPTION = 2_000;
    /** Agent system prompts and chat-message content. */
    public static final int TEXT = 20_000;
    /** allowedTools and allowedKbs, counted as sent (before duplicates are dropped). */
    public static final int LIST = 50;

    private RequestLimits() {
    }

}
