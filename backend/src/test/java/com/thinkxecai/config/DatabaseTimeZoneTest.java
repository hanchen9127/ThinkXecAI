package com.thinkxecai.config;

import com.thinkxecai.mapper.MapperTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every pooled connection runs in UTC whatever the JVM zone (tests run in Australia/Sydney), so
 * timestamps written with now() / clock_timestamp() into TIMESTAMP columns never go back when
 * daylight saving time ends (ADR 034).
 */
class DatabaseTimeZoneTest extends MapperTestSupport {

    @Test
    void pooledConnectionsUseUtc() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_setting('TimeZone')", String.class)).isEqualTo("UTC");
    }

    @Test
    void storedTimestampsAreUtc() {
        UUID session = insertChatSession(null, "s");
        UUID message = jdbcTemplate.queryForObject(
                "INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', 'hi') RETURNING id", UUID.class, session);

        Boolean utc = jdbcTemplate.queryForObject("""
                SELECT created_at BETWEEN (clock_timestamp() AT TIME ZONE 'UTC') - interval '1 minute'
                                      AND (clock_timestamp() AT TIME ZONE 'UTC')
                FROM chat_message WHERE id = ?""", Boolean.class, message);
        assertThat(utc).isTrue();
    }

}
