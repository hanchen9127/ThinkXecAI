package com.thinkxecai.agent;

import com.thinkxecai.service.ServiceTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whole agent runs against the real DeepSeek and Gemini APIs (needs real {@code THINKXECAI_DEEPSEEK_API_KEY}
 * and {@code THINKXECAI_GEMINI_API_KEY}; costs about 20 requests). Each run uses the fixed sample tools,
 * so the answers are checked only for their shape: tool calls with ids answered by tool rows, and a final
 * plain answer. The session that switches models checks that one model accepts a history the other wrote:
 * stored tool calls carry no Gemini thought signatures. Rows are logged, since only a real run shows how
 * each model splits the work.
 */
@Tag("external")
class AgentRunExternalTest extends ServiceTestSupport {

    private static final Logger log = LoggerFactory.getLogger(AgentRunExternalTest.class);
    private static final Set<String> NOTICES = Set.of(AgentNotices.MODEL_FAILED, AgentNotices.AGENT_GONE,
            AgentNotices.MODEL_UNAVAILABLE, AgentNotices.BUSY, AgentNotices.UNEXPECTED, AgentNotices.MAX_STEPS);

    @Autowired
    private AgentRunner runner;

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-chat", "gemini-3.8-flash"})
    void aMultiStepToolRunEndsWithAnAnswer(String model) {
        UUID agent = agentOn(model);
        UUID session = insertChatSession(agent, "external");

        say(session, "What is the weather in my city today? Find my city and today's date with the tools, "
                + "then get the weather and tell me.");
        runner.run(agent, session);

        List<Map<String, Object>> rows = rows(session);
        log(model, rows);
        assertToolRowsAnswerEarlierCalls(rows);
        assertThat(toolNames(rows)).contains("getCity", "getDate", "weather");
        assertEndsWithAnAnswer(rows);
    }

    @Test
    void oneSessionCanSwitchModelsBetweenRuns() {
        UUID agent = agentOn("gemini-3.8-flash");
        UUID session = insertChatSession(agent, "external");

        say(session, "What is today's date? Use the getDate tool.");
        runner.run(agent, session);
        useModel(agent, "deepseek-chat");
        say(session, "Which city do I have? Use the getCity tool.");
        runner.run(agent, session);
        useModel(agent, "gemini-3.8-flash");
        say(session, "What is the weather there on that date? Use the weather tool.");
        runner.run(agent, session);

        List<Map<String, Object>> rows = rows(session);
        log("gemini → deepseek → gemini", rows);
        assertToolRowsAnswerEarlierCalls(rows);
        assertThat(toolNames(rows)).contains("getDate", "getCity", "weather");
        assertThat(rows).extracting(row -> row.get("role")).filteredOn("user"::equals).hasSize(3);
        // each run ended with a plain answer: the row before each later question, and the last row
        for (int i = 1; i < rows.size(); i++) {
            if ("user".equals(rows.get(i).get("role"))) {
                assertEndsWithAnAnswer(rows.subList(0, i));
            }
        }
        assertEndsWithAnAnswer(rows);
    }

    @Test
    void aQueuedGeminiRunReadsThePreviousRunsToolCalls() {
        // ADR 042: a message sent during a run is stored before that run's rows, so the next run's current
        // turn (since the last user message) holds the previous run's calls, rebuilt without thought signatures
        UUID agent = agentOn("gemini-3.8-flash");
        UUID session = insertChatSession(agent, "external");

        say(session, "What is today's date? Use the getDate tool.");
        say(session, "And which city do I have? Use the getCity tool.");
        runner.run(agent, session);
        int firstRun = rows(session).size();
        runner.run(agent, session);

        List<Map<String, Object>> rows = rows(session);
        log("queued gemini", rows);
        assertToolRowsAnswerEarlierCalls(rows);
        assertEndsWithAnAnswer(rows.subList(0, firstRun));
        assertThat(rows).hasSizeGreaterThan(firstRun);
        assertEndsWithAnAnswer(rows);
    }

    private UUID agentOn(String model) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent (name, model) VALUES ('external', ?) RETURNING id", UUID.class, model);
    }

    private void useModel(UUID agent, String model) {
        jdbcTemplate.update("UPDATE agent SET model = ? WHERE id = ?", model, agent);
    }

    private void say(UUID session, String text) {
        jdbcTemplate.update("INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', ?)", session, text);
    }

    private List<Map<String, Object>> rows(UUID session) {
        return jdbcTemplate.queryForList("SELECT role, content, metadata::text AS metadata, "
                + "metadata->'toolResponse'->>'id' AS response_id, metadata->'toolResponse'->>'name' AS response_name, "
                + "(SELECT string_agg(c->>'id', ' ') FROM jsonb_array_elements(COALESCE(metadata->'toolCalls', '[]')) c) AS call_ids "
                + "FROM chat_message WHERE session_id = ? ORDER BY created_at", session);
    }

    private static void log(String label, List<Map<String, Object>> rows) {
        rows.forEach(row -> log.info("{} | {}: {} | {}", label, row.get("role"), row.get("content"), row.get("metadata")));
    }

    /** Every tool row answers a call, by id, of the assistant row it follows; every id is set. */
    private static void assertToolRowsAnswerEarlierCalls(List<Map<String, Object>> rows) {
        Set<String> openCalls = new HashSet<>();
        for (Map<String, Object> row : rows) {
            if ("assistant".equals(row.get("role"))) {
                openCalls.clear();
                String ids = (String) row.get("call_ids");
                for (String id : ids == null ? new String[0] : ids.split(" ")) {
                    if (!id.isBlank()) {
                        openCalls.add(id);
                    }
                }
            } else if ("tool".equals(row.get("role"))) {
                assertThat((String) row.get("response_id")).isNotBlank().isIn(openCalls);
                openCalls.remove(row.get("response_id"));
            } else {
                openCalls.clear();
            }
        }
    }

    private static Set<Object> toolNames(List<Map<String, Object>> rows) {
        Set<Object> names = new HashSet<>();
        rows.stream().filter(row -> "tool".equals(row.get("role"))).forEach(row -> names.add(row.get("response_name")));
        return names;
    }

    private static void assertEndsWithAnAnswer(List<Map<String, Object>> rows) {
        Map<String, Object> last = rows.get(rows.size() - 1);
        assertThat(last.get("role")).isEqualTo("assistant");
        assertThat(last.get("metadata")).isNull();
        assertThat((String) last.get("content")).isNotBlank().isNotIn(NOTICES);
    }

}
