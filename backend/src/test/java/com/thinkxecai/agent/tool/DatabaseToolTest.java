package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.mapper.MapperTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read-only guarantee comes from the database, so it is tested against the real one (ISSUE-005).
 * Tests run without the test transaction, as the agent loop does. Attacks target a table the test
 * creates and drops itself, so a broken guard cannot damage the seeded sample data.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatabaseToolTest extends MapperTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private DatabaseTool tool;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createProbeTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS database_tool_probe, database_tool_select_into");
        jdbcTemplate.execute("CREATE TABLE database_tool_probe (k TEXT PRIMARY KEY, v TEXT)");
        jdbcTemplate.update("INSERT INTO database_tool_probe VALUES ('a', '1'), ('b', '2'), ('c', '3')");
    }

    @AfterEach
    void dropProbeTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS database_tool_probe, database_tool_select_into");
    }

    @Test
    void theSeededSampleTablesCanBeQueried() {
        int products = countRows("t_product", "true");

        String answer = tool.databaseQuery("SELECT sku, name FROM t_product ORDER BY sku LIMIT 3");

        assertThat(products).isPositive();
        assertThat(answer).startsWith("| sku | name |\n| --- | --- |\n")
                .endsWith("(" + Math.min(products, 3) + (Math.min(products, 3) == 1 ? " row)" : " rows)"));
    }

    @Test
    void rowsAreFormattedAsATableWithNullsPipesAndLineBreaksKeptInTheirCell() {
        assertThat(tool.databaseQuery("SELECT 1 AS n, NULL AS empty, 'a|b' AS pipe, E'x\\ny' AS lines"))
                .isEqualTo("| n | empty | pipe | lines |\n| --- | --- | --- | --- |\n| 1 | NULL | a\\|b | x y |\n(1 row)");
        assertThat(tool.databaseQuery("SELECT k FROM database_tool_probe WHERE false"))
                .isEqualTo("| k |\n| --- |\n(0 rows)");
    }

    @Test
    void lowercaseAndLeadingWhitespaceAreAccepted() {
        assertThat(tool.databaseQuery("  \n select count(*) AS n from database_tool_probe")).contains("| 3 |");
    }

    @Test
    void theQueryRunsReadOnlyWithTheTimeout() {
        assertThat(tool.databaseQuery(
                "SELECT current_setting('transaction_read_only') AS ro, current_setting('statement_timeout') AS t"))
                .contains("| on | 5s |");
    }

    @Test
    void aWriteAfterASemicolonIsRejectedByTheDatabase() {
        String answer = tool.databaseQuery("SELECT 1; DROP TABLE database_tool_probe");

        assertThat(answer).startsWith("Query failed:").contains("read-only transaction");
        assertThat(probeTableExists("database_tool_probe")).isTrue();
    }

    @Test
    void aWriteInsideACteIsRejectedByTheDatabase() {
        String answer = tool.databaseQuery("SELECT * FROM (SELECT 1) s; WITH d AS (DELETE FROM database_tool_probe RETURNING *) SELECT * FROM d");

        assertThat(answer).contains("read-only transaction");
        assertThat(countRows("database_tool_probe", "true")).isEqualTo(3);
    }

    @Test
    void selectIntoCannotCreateATable() {
        String answer = tool.databaseQuery("SELECT * INTO database_tool_select_into FROM database_tool_probe");

        assertThat(answer).contains("read-only transaction");
        assertThat(probeTableExists("database_tool_select_into")).isFalse();
    }

    @Test
    void statementsNotStartingWithSelectAreRefusedBeforeTheDatabase() {
        assertThat(tool.databaseQuery("UPDATE database_tool_probe SET v = 'x'")).isEqualTo("Only SELECT statements are allowed.");
        assertThat(tool.databaseQuery("DELETE FROM database_tool_probe")).isEqualTo("Only SELECT statements are allowed.");
        assertThat(tool.databaseQuery(" ")).isEqualTo("The SQL must not be blank.");
        assertThat(tool.databaseQuery(null)).isEqualTo("The SQL must not be blank.");
        assertThat(countRows("database_tool_probe", "v = 'x'")).isZero();
        assertThat(countRows("database_tool_probe", "true")).isEqualTo(3);
    }

    @Test
    void aSlowQueryIsStoppedAfterFiveSeconds() {
        Instant start = Instant.now();

        String answer = tool.databaseQuery("SELECT pg_sleep(10)");

        assertThat(answer).isEqualTo("Query stopped: it ran longer than 5 seconds.");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(9));
    }

    /** set_config can turn off the server's statement_timeout for the next statement, but not the driver's. */
    @Test
    void theTimeoutCannotBeSwitchedOffByTheQuery() {
        Instant start = Instant.now();

        String answer = tool.databaseQuery("SELECT set_config('statement_timeout', '0', true); SELECT pg_sleep(10)");

        assertThat(answer).isEqualTo("Query stopped: it ran longer than 5 seconds.");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(9));
    }

    @Test
    void resultsAreCappedAt200RowsAndTruncationIsMarked() {
        String truncated = tool.databaseQuery("SELECT generate_series(1, 1000) AS n");
        String full = tool.databaseQuery("SELECT generate_series(1, 200) AS n");

        assertThat(truncated.lines().filter(line -> line.matches("\\| \\d+ \\|"))).hasSize(200);
        assertThat(truncated).endsWith("| 200 |\n(truncated to 200 rows)");
        assertThat(full).endsWith("| 200 |\n(200 rows)");
    }

    @Test
    void errorsReturnTheDatabaseMessageWithoutJavaDetails() {
        String answer = tool.databaseQuery("SELECT * FROM no_such_table");

        assertThat(answer).startsWith("Query failed: ERROR: relation \"no_such_table\" does not exist");
        assertThat(answer).doesNotContain("Exception", "java.", "at org.");
    }

    @Test
    void failedQueriesLeaveNoReadOnlyOrTimeoutSettingBehind() {
        tool.databaseQuery("SELECT 1; DROP TABLE database_tool_probe");
        tool.databaseQuery("SELECT pg_sleep(10)");

        assertThat(tool.databaseQuery("SELECT count(*) AS n FROM database_tool_probe")).contains("| 3 |");
        for (int i = 0; i < 10; i++) {
            assertThat(jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            assertThat(jdbcTemplate.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("0");
        }
    }

    /** The tool runs in its own transaction, so a caller's transaction is neither made read-only nor time-limited. */
    @Test
    void aCallersTransactionIsNotChangedByTheTool() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            assertThat(tool.databaseQuery("SELECT current_setting('transaction_read_only') AS ro")).contains("| on |");
            assertThat(jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            assertThat(jdbcTemplate.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("0");
            assertThat(jdbcTemplate.update("INSERT INTO database_tool_probe VALUES ('d', '4')")).isEqualTo(1);
            status.setRollbackOnly();
        });
    }

    @Test
    void theModelSeesDatabaseQueryWithOneSqlArgument() throws Exception {
        ToolCallback callback = MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks()[0];
        JsonNode schema = JSON.readTree(callback.getToolDefinition().inputSchema());

        assertThat(tool.getName()).isEqualTo("dataBaseTool");
        assertThat(tool.getType()).isEqualTo(ToolType.OPTIONAL);
        assertThat(callback.getToolDefinition().name()).isEqualTo("databaseQuery");
        assertThat(schema.get("properties").fieldNames()).toIterable().containsExactly("sql");
    }

    private boolean probeTableExists(String table) {
        return jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table);
    }

}
