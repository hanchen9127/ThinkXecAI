package com.thinkxecai.mapper;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the base class itself: the helpers create linked rows, and each test is rolled back. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MapperTestSupportTest extends MapperTestSupport {

    private static final String MARKER = "mapper-test-support-rollback-probe";

    @Test
    @Order(1)
    void helpersCreateRowsLinkedByForeignKeys() {
        UUID agentId = insertAgent("agent");
        UUID sessionId = insertChatSession(agentId, "session");
        UUID kbId = insertKnowledgeBase(MARKER);
        UUID documentId = insertDocument(kbId, "doc.md");

        assertThat(countRows("chat_session", "id = ? AND agent_id = ?", sessionId, agentId)).isEqualTo(1);
        assertThat(countRows("document", "id = ? AND kb_id = ?", documentId, kbId)).isEqualTo(1);
        assertThat(countRows("knowledge_base", "name = ?", MARKER)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void rowsFromThePreviousTestWereRolledBack() {
        assertThat(countRows("knowledge_base", "name = ?", MARKER)).isZero();
    }

    @Test
    @Order(3)
    void chatSessionCanBeCreatedWithoutAnAgent() {
        UUID sessionId = insertChatSession(null, "orphan");

        assertThat(countRows("chat_session", "id = ? AND agent_id IS NULL", sessionId)).isEqualTo(1);
    }

}
