package com.thinkxecai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool data as the models accept it. Spring AI's Gemini model parses every tool result and every call's
 * arguments as JSON and fails the whole request on anything else, so a plain error text, an empty result,
 * or a malformed argument string in one row would break every later Gemini call of the session.
 */
public final class ToolJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ToolJson() {
    }

    /** The result unchanged if it is one JSON value, else the text as a JSON string. */
    public static String result(String data) {
        String text = data == null ? "" : data;
        return isJson(text) ? text : quote(text);
    }

    /** The arguments unchanged if they are one JSON value, else an empty object. */
    public static String arguments(String arguments) {
        return arguments != null && isJson(arguments) ? arguments : "{}";
    }

    private static boolean isJson(String text) {
        if (text.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(text);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A string is always serializable", e);
        }
    }

}
