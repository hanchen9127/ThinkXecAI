package com.thinkxecai.common;

/**
 * Makes client-supplied text safe to put in a log line: line breaks and other control characters are
 * written as escapes, so a request cannot end the current log line and start a forged one.
 */
public final class LogText {

    private LogText() {
    }

    public static String safe(Object value) {
        String text = String.valueOf(value);
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> safe.append("\\n");
                case '\r' -> safe.append("\\r");
                case '\t' -> safe.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        safe.append(String.format("\\u%04x", (int) c));
                    } else {
                        safe.append(c);
                    }
                }
            }
        }
        return safe.toString();
    }

}
