package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.mapper.MapperTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * The read-only guarantee comes from the database, so it is tested against the real one (ISSUE-005,
 * ADR 030). Tests run without the test transaction, as the agent loop does. Attacks target a table and
 * a sequence the test creates and drops itself, so a broken guard cannot damage the seeded sample data.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatabaseToolTest extends MapperTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ONE_STATEMENT_ONLY =
            "Only one SQL statement is allowed; remove the extra statements after the first semicolon.";

    @Autowired
    private DatabaseTool tool;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createProbeObjects() {
        dropProbeObjects();
        jdbcTemplate.execute("CREATE TABLE database_tool_probe (k TEXT PRIMARY KEY, v TEXT)");
        jdbcTemplate.execute("CREATE SEQUENCE database_tool_probe_seq");
        jdbcTemplate.update("INSERT INTO database_tool_probe VALUES ('a', '1'), ('b', '2'), ('c', '3')");
    }

    @AfterEach
    void dropProbeObjects() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS database_tool_probe, database_tool_select_into");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS database_tool_probe_seq");
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
    void theQueryRunsInUtcLikeTheApplication() {
        assertThat(tool.databaseQuery("SELECT current_setting('TimeZone') AS tz")).contains("| UTC |");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1; DROP TABLE database_tool_probe",
            "SELECT 1;DROP TABLE database_tool_probe;",
            "SELECT 1; SELECT 2",
            "SELECT * FROM (SELECT 1) s; WITH d AS (DELETE FROM database_tool_probe RETURNING *) SELECT * FROM d",
            "SELECT set_config('statement_timeout', '0', true); SELECT pg_sleep(10)"})
    void moreThanOneStatementIsRefusedBeforeTheDatabase(String sql) {
        assertThat(tool.databaseQuery(sql)).isEqualTo(ONE_STATEMENT_ONLY);
        assertThat(probeTableExists("database_tool_probe")).isTrue();
        assertThat(countRows("database_tool_probe", "true")).isEqualTo(3);
    }

    /** A statement that ends the read-only transaction would let the next one run read-write. */
    @ParameterizedTest
    @ValueSource(strings = {"COMMIT", "ROLLBACK", "END", "ABORT"})
    void endingTheTransactionMidStringCannotUnlockWrites(String ending) {
        String answer = tool.databaseQuery("SELECT 1; " + ending + "; DROP TABLE database_tool_probe");

        assertThat(answer).isEqualTo(ONE_STATEMENT_ONLY);
        assertThat(probeTableExists("database_tool_probe")).isTrue();
    }

    /**
     * JDBC escape syntax ({@code {d ...}}, {@code {fn ...}}) is not processed, so the driver splits
     * exactly the text that was counted; rewriting it first could turn one counted statement into several.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT {d '}', '{fn concat(a, b)}; COMMIT; DROP TABLE database_tool_probe; --'",
            "SELECT {d '}', '{fn ucase(a}; COMMIT; DROP TABLE database_tool_probe; --)}'",
            "SELECT {d '}' , '{fn locate(a, b''; COMMIT; DROP TABLE database_tool_probe; --, c)}'",
            "SELECT {d 'x}', '; COMMIT; DROP TABLE database_tool_probe; SELECT 1 --'"})
    void jdbcEscapesCannotSplitOneCountedStatement(String sql) {
        String answer = tool.databaseQuery(sql);

        assertThat(probeTableExists("database_tool_probe")).as(answer).isTrue();
        assertThat(answer).as("no part may run read-write").doesNotStartWith("|");
    }

    @Test
    void jdbcEscapesAreSentAsWritten() {
        assertThat(tool.databaseQuery("SELECT {fn ucase('a')} AS v")).startsWith("Query failed:");
    }

    /** The driver's own parser decides, so semicolons inside literals and comments are not statement breaks. */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 'a;b' AS v;",
            "SELECT $$x;y$$ AS v",
            "SELECT 1 AS v -- ; DROP TABLE database_tool_probe",
            "SELECT 1 AS v /* ; DROP TABLE database_tool_probe */"})
    void semicolonsInLiteralsAndCommentsAndATrailingOneAreOneStatement(String sql) {
        assertThat(tool.databaseQuery(sql)).startsWith("| v |").endsWith("(1 row)");
        assertThat(probeTableExists("database_tool_probe")).isTrue();
    }

    @Test
    void aWriteHiddenInAFunctionIsRejectedByTheDatabase() {
        String answer = tool.databaseQuery("SELECT nextval('database_tool_probe_seq')");

        assertThat(answer).startsWith("Query failed:").contains("read-only transaction");
        assertThat(jdbcTemplate.queryForObject("SELECT nextval('database_tool_probe_seq')", Long.class)).isEqualTo(1L);
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

    /** The server's timer already runs for the statement, and the driver's cannot be reached at all. */
    @Test
    void theTimeoutCannotBeSwitchedOffByTheQuery() {
        Instant start = Instant.now();

        String answer = tool.databaseQuery("SELECT set_config('statement_timeout', '0', true), pg_sleep(10)");

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
    void longValuesAndLongAnswersAreShortenedAndMarked() {
        String longValue = tool.databaseQuery("SELECT repeat('x', 5000) AS v");
        String longAnswer = tool.databaseQuery("SELECT repeat('y', 500) AS v FROM generate_series(1, 100)");

        assertThat(longValue).contains("| " + "x".repeat(1_000) + "… (5000 characters, shortened) |").endsWith("(1 row)");
        assertThat(longAnswer).hasSizeLessThanOrEqualTo(20_000 + 60)
                .contains("(truncated after ")
                .endsWith(" rows at 20,000 characters)");
    }

    /** The driver stops reading a result over 16 MB instead of loading it into memory and cutting it afterwards. */
    @Test
    void aResultOverTheBufferLimitIsRefusedByTheDriver() {
        String tooLarge = tool.databaseQuery("SELECT repeat('x', 20000000) AS v");
        String belowLimit = tool.databaseQuery("SELECT repeat('x', 5000000) AS v");

        assertThat(tooLarge).startsWith("Query failed:").doesNotContain("xxxxxxxxxx");
        assertThat(belowLimit).contains("… (5000000 characters, shortened) |").endsWith("(1 row)");
    }

    @Test
    void errorsReturnTheDatabaseMessageWithoutJavaDetails() {
        String answer = tool.databaseQuery("SELECT * FROM no_such_table");

        assertThat(answer).startsWith("Query failed: ERROR: relation \"no_such_table\" does not exist");
        assertThat(answer).doesNotContain("Exception", "java.", "at org.");
    }

    /** Each call has a connection of its own, so nothing a query does to its session outlives the call. */
    @Test
    void sessionSettingsAndAdvisoryLocksEndWithTheCall() throws Exception {
        String answer = tool.databaseQuery("SELECT set_config('search_path', 'pg_catalog', false) AS a, "
                + "set_config('statement_timeout', '1ms', false) AS b, pg_advisory_lock(730001) AS c");

        assertThat(answer).endsWith("(1 row)");
        assertThat(tool.databaseQuery("SELECT count(*) AS n FROM database_tool_probe")).contains("| 3 |");
        for (int i = 0; i < 10; i++) {
            assertThat(jdbcTemplate.queryForObject("SHOW search_path", String.class)).isEqualTo("\"$user\", public");
            assertThat(jdbcTemplate.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("0");
        }
        assertThat(advisoryLockReleased(730001)).isTrue();
    }

    @Test
    void theQueryDoesNotRunOnAPooledConnection() {
        assertThat(tool.databaseQuery("SELECT current_setting('application_name') AS app"))
                .contains("| thinkxecai-database-tool |");
    }

    @Test
    void failedQueriesLeaveNoReadOnlyOrTimeoutSettingBehind() {
        tool.databaseQuery("SELECT * INTO database_tool_select_into FROM database_tool_probe");
        tool.databaseQuery("SELECT pg_sleep(10)");

        assertThat(tool.databaseQuery("SELECT count(*) AS n FROM database_tool_probe")).contains("| 3 |");
        for (int i = 0; i < 10; i++) {
            assertThat(jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            assertThat(jdbcTemplate.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("0");
        }
    }

    /** The tool runs on its own connection, so a caller's transaction is neither made read-only nor time-limited. */
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

    /** The server releases a closed session's locks shortly after the client disconnects. */
    private boolean advisoryLockReleased(long key) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            Integer held = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objid = ?", Integer.class, key);
            if (held != null && held == 0) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private boolean probeTableExists(String table) {
        return jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table);
    }

}
