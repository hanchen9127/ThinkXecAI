package com.thinkxecai.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogTextTest {

    @Test
    void lineBreaksAndOtherControlCharactersCannotStartAFakeLogLine() {
        assertThat(LogText.safe("tool\n2026-10-17 ERROR forged\r\tx\u0007"))
                .isEqualTo("tool\\n2026-10-17 ERROR forged\\r\\tx\\u0007");
    }

    @Test
    void ordinaryTextAndNullAreUnchanged() {
        assertThat(LogText.safe("allowedTools: not an optional tool: wébTool, 工具")).isEqualTo("allowedTools: not an optional tool: wébTool, 工具");
        assertThat(LogText.safe(null)).isEqualTo("null");
    }

}
