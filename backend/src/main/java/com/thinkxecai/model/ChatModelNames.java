package com.thinkxecai.model;

/**
 * The chat models an agent can use. Agent requests are validated against these names, the API
 * description lists them as an enum (so the frontend type is a union of them), and the Phase 10
 * ChatClient registry registers its beans under them.
 */
public final class ChatModelNames {

    public static final String DEEPSEEK_CHAT = "deepseek-chat";
    public static final String GEMINI_FLASH = "gemini-3.8-flash";

    /** For {@code @Pattern}: exactly one of the names above. */
    public static final String PATTERN = "deepseek-chat|gemini-3\\.8-flash";
    public static final String PATTERN_MESSAGE = "must be deepseek-chat or gemini-3.8-flash";

    private ChatModelNames() {
    }

}
