package com.thinkxecai.agent.tool;

import com.thinkxecai.model.ToolFailureKind;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.NativeQuery;
import org.postgresql.core.Parser;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Lets the model read the database with SQL (ISSUE-005, ADR 028, ADR 030). The database does the
 * enforcing; the checks here only keep a query inside it:
 * <ul>
 *   <li>Exactly one statement, counted with the JDBC driver's own parser on the exact text it will
 *       send: JDBC escape processing is off (rewriting {@code {fn ...}} first could change the split),
 *       and the connection's own {@code standard_conforming_strings} is used. With the extended query
 *       protocol the server also refuses several commands in one statement. A second statement could
 *       end the read-only transaction ({@code SELECT 1; COMMIT; DROP ...} would run the {@code DROP}
 *       in a new read-write transaction) or switch the timeout off for itself.</li>
 *   <li>The statement must start with {@code SELECT} and runs in a read-only transaction, so
 *       PostgreSQL rejects any write it hides ({@code SELECT INTO}, functions that write).</li>
 *   <li>It runs on a connection of its own, opened for this call and closed afterwards, never one
 *       from the application's pool. Session settings it changes ({@code set_config(..., false)})
 *       and session-level advisory locks end with that connection. The transaction is also always
 *       rolled back.</li>
 *   <li>It is stopped after 5 seconds by the server's {@code statement_timeout} and by the
 *       driver's query timeout; a silent server or network is given up after 10 seconds. The driver
 *       refuses results over 16 MB before they fill memory; at most 200 rows, 1,000 characters per
 *       value and 20,000 characters in total are returned, each cut marked.</li>
 * </ul>
 * Known limit, accepted for now: the application connects as a superuser, so superuser functions that
 * do not write (reading server files, ending other connections) still work, and the application's own
 * tables are visible. A dedicated read-only role is a v2 candidate.
 */
@Slf4j
@Component
public class DatabaseTool implements Tool {

    static final int MAX_ROWS = 200;
    static final int MAX_VALUE_CHARS = 1_000;
    static final int MAX_ANSWER_CHARS = 20_000;
    static final int TIMEOUT_SECONDS = 5;
    static final int SOCKET_TIMEOUT_SECONDS = 10;
    static final String MAX_RESULT_BUFFER = "16M";

    /** PostgreSQL {@code query_canceled}: statement timeout or a cancel sent by the driver's query timeout. */
    private static final String QUERY_CANCELED = "57014";
    /** SQLSTATE class 42: syntax error or access rule violation, such as an unknown column. */
    private static final String MALFORMED_SQL_CLASS = "42";
    /** A write in the read-only transaction, or a privilege the database user lacks: the rules holding. */
    private static final Set<String> REFUSED_STATES = Set.of("25006", "42501");

    private final String url;
    private final Properties connectionProperties;

    public DatabaseTool(DataSourceProperties dataSource) {
        this.url = dataSource.determineUrl();
        this.connectionProperties = new Properties();
        connectionProperties.setProperty("user", dataSource.determineUsername());
        if (dataSource.determinePassword() != null) {
            connectionProperties.setProperty("password", dataSource.determinePassword());
        }
        connectionProperties.setProperty("connectTimeout", String.valueOf(TIMEOUT_SECONDS));
        connectionProperties.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        connectionProperties.setProperty("maxResultBuffer", MAX_RESULT_BUFFER);
        connectionProperties.setProperty("preferQueryMode", "extended");
        connectionProperties.setProperty("ApplicationName", "thinkxecai-database-tool");
    }

    @Override
    public String getName() {
        return "dataBaseTool";
    }

    @Override
    public String getDescription() {
        return "Runs read-only SQL queries against the PostgreSQL database, including the sample e-commerce tables.";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "databaseQuery",
            description = "Run one read-only SQL SELECT statement on the PostgreSQL database and get the rows back as a table. "
                    + "The sample e-commerce tables are named t_* (users, products, orders, payments, shipments, comments). "
                    + "At most 200 rows and 20,000 characters are returned, long values are shortened, "
                    + "and a query is stopped after 5 seconds.")
    public String databaseQuery(@ToolParam(description = "One PostgreSQL SELECT statement") String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ToolCallFailure(ToolFailureKind.INVALID_ARGUMENTS, "The SQL must not be blank.");
        }
        if (!sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw new ToolCallFailure(ToolFailureKind.REFUSED, "Only SELECT statements are allowed.");
        }
        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            if (statementCount(sql, connection) != 1) {
                throw new ToolCallFailure(ToolFailureKind.REFUSED,
                        "Only one SQL statement is allowed; remove the extra statements after the first semicolon.");
            }
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // Send the text exactly as counted: no rewriting of JDBC escapes such as {fn ...}.
                statement.setEscapeProcessing(false);
                statement.execute("SET TRANSACTION READ ONLY");
                statement.execute("SET LOCAL statement_timeout = '" + TIMEOUT_SECONDS + "s'");
                // Same zone as the application's connections, so now() and stored timestamps agree.
                statement.execute("SET LOCAL TIME ZONE 'UTC'");
                // One row more than returned, so a full result can be told apart from a truncated one.
                statement.setMaxRows(MAX_ROWS + 1);
                statement.setQueryTimeout(TIMEOUT_SECONDS);
                try (ResultSet rs = statement.executeQuery(sql)) {
                    return toTable(rs);
                }
            } finally {
                rollbackQuietly(connection);
            }
        } catch (SQLException e) {
            throw failure(e);
        } catch (ToolCallFailure e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Database tool query failed", e);
            throw new ToolCallFailure(ToolFailureKind.TOOL_ERROR, "Query failed because of an internal error.");
        }
    }

    /** Closing the connection right after ends the transaction anyway, so a failed rollback must not hide the query's error. */
    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.debug("Rollback before closing the database tool connection failed: {}", e.getMessage());
        }
    }

    /** Statements as the driver will split and send this text on this connection; 0 when it cannot be parsed. */
    private static int statementCount(String sql, Connection connection) {
        try {
            boolean standardConformingStrings = connection.unwrap(BaseConnection.class).getStandardConformingStrings();
            List<NativeQuery> queries = Parser.parseJdbcSql(sql, standardConformingStrings, false, true, false, false);
            return queries.size();
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * The database's own message tells the model what to fix; no stack trace or Java class names. SQLSTATE
     * class 42 (syntax error or access rule violation, such as an unknown column) is the model's SQL being
     * wrong, except a missing privilege; that and a write in the read-only transaction are the rules refusing;
     * anything else is the database failing (ADR 064).
     */
    private static ToolCallFailure failure(SQLException e) {
        if (QUERY_CANCELED.equals(e.getSQLState())) {
            return new ToolCallFailure(ToolFailureKind.TOOL_ERROR,
                    "Query stopped: it ran longer than " + TIMEOUT_SECONDS + " seconds.");
        }
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        ToolFailureKind kind = REFUSED_STATES.contains(state) ? ToolFailureKind.REFUSED
                : state.startsWith(MALFORMED_SQL_CLASS) ? ToolFailureKind.INVALID_ARGUMENTS
                : ToolFailureKind.TOOL_ERROR;
        return new ToolCallFailure(kind, "Query failed: " + e.getMessage());
    }

    /**
     * A Markdown table, followed by the row count or a note on what was cut. A blank line comes first:
     * a line right under a table is read as one more row.
     */
    private static String toTable(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        StringBuilder out = new StringBuilder("|");
        for (int i = 1; i <= columns; i++) {
            out.append(' ').append(cell(meta.getColumnLabel(i))).append(" |");
        }
        out.append("\n|").append(" --- |".repeat(columns)).append('\n');

        int rows = 0;
        while (rs.next()) {
            if (rows == MAX_ROWS) {
                return out.append("\n(truncated to ").append(MAX_ROWS).append(" rows)").toString();
            }
            StringBuilder row = new StringBuilder("|");
            for (int i = 1; i <= columns; i++) {
                row.append(' ').append(cell(rs.getString(i))).append(" |");
            }
            row.append('\n');
            if (out.length() + row.length() > MAX_ANSWER_CHARS) {
                return out.append("\n(truncated after ").append(rows).append(rows == 1 ? " row" : " rows")
                        .append(" at ").append(String.format(Locale.ROOT, "%,d", MAX_ANSWER_CHARS)).append(" characters)")
                        .toString();
            }
            out.append(row);
            rows++;
        }
        return out.append("\n(").append(rows).append(rows == 1 ? " row)" : " rows)").toString();
    }

    /**
     * Keeps one value in one table cell: NULL spelled out, pipes escaped, line breaks flattened, and
     * values longer than 1,000 characters cut with a note.
     */
    private static String cell(String value) {
        if (value == null) {
            return "NULL";
        }
        String shortened = value.length() > MAX_VALUE_CHARS
                ? value.substring(0, MAX_VALUE_CHARS) + "… (" + value.length() + " characters, shortened)"
                : value;
        return shortened.replace("|", "\\|").replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

}
