package com.thinkxecai.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Base class for mapper tests against the real PostgreSQL database.
 * <p>
 * Every test method runs in a transaction that is rolled back afterwards, so tests leave no rows
 * behind and do not see each other's data. All subclasses share one Spring context configuration,
 * so the context is started once per test run.
 * <p>
 * The {@code insert*} helpers create parent rows for foreign keys with plain SQL through
 * {@link JdbcTemplate}, not through other mappers: a test then depends only on the mapper it is
 * testing. JdbcTemplate and MyBatis join the same test transaction, so the mapper under test sees
 * these rows.
 */
@SpringBootTest
@Transactional
public abstract class MapperTestSupport {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected UUID insertAgent(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model) VALUES (?, 'deepseek-chat') RETURNING id", UUID.class, name);
    }

    protected UUID insertKnowledgeBase(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO knowledge_base (name) VALUES (?) RETURNING id", UUID.class, name);
    }

    /** {@code agentId} may be null: chat_session.agent_id is nullable (ON DELETE SET NULL). */
    protected UUID insertChatSession(UUID agentId, String title) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO chat_session (agent_id, title) VALUES (?, ?) RETURNING id", UUID.class, agentId, title);
    }

    protected UUID insertDocument(UUID kbId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO document (kb_id, name) VALUES (?, ?) RETURNING id", UUID.class, kbId, name);
    }

    protected int countRows(String table, String whereClause, Object... args) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + whereClause, Integer.class, args);
        return count == null ? 0 : count;
    }

}
