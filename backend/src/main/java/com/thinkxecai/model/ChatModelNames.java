package com.thinkxecai.model;

import java.util.List;

/**
 * The chat models an agent can use. Agent requests are validated against these names, the API
 * description lists them as an enum (so the frontend type is a union of them), and the chat
 * clients are registered under them (ChatModelConfig, ChatClientRegistry).
 */
public final class ChatModelNames {

    public static final String DEEPSEEK_CHAT = "deepseek-chat";
    public static final String GEMINI_FLASH = "gemini-3.8-flash";

    /** Every name above; the ChatClient registry must hold exactly these. */
    public static final List<String> ALL = List.of(DEEPSEEK_CHAT, GEMINI_FLASH);

    /** For {@code @Pattern}: exactly one of the names above (a test keeps it equal to {@link #ALL}). */
    public static final String PATTERN = "deepseek-chat|gemini-3\\.8-flash";
    public static final String PATTERN_MESSAGE = "must be deepseek-chat or gemini-3.8-flash";

    private ChatModelNames() {
    }

}
