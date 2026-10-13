package com.thinkxecai.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Lets the model read the database with SQL. The {@code SELECT} prefix check only gives a quick,
 * friendly refusal; the database enforces the rest (ISSUE-005, ADR 028): every query runs in its own
 * read-only transaction, so writes hidden after a {@code ;}, inside a CTE, or in {@code SELECT INTO}
 * are rejected by PostgreSQL. A query is stopped after 5 seconds twice over: by the server's
 * {@code statement_timeout}, and by the driver's query timeout, which a query cannot switch off with
 * {@code set_config}. At most 200 rows are returned.
 * <p>
 * Known limit, accepted for now: the application connects as a superuser, so superuser functions that
 * do not write (reading server files, ending other connections) still work, and the application's own
 * tables are visible. A dedicated read-only role is a v2 candidate.
 */
@Slf4j
@Component
public class DatabaseTool implements Tool {

    static final int MAX_ROWS = 200;
    static final int TIMEOUT_SECONDS = 5;

    /** PostgreSQL {@code query_canceled}: statement timeout or a cancel sent by the driver's query timeout. */
    private static final String QUERY_CANCELED = "57014";

    private final TransactionTemplate readOnlyTransaction;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseTool(DataSource dataSource, PlatformTransactionManager transactionManager) {
        // Always a new transaction: SET TRANSACTION READ ONLY must be the first statement of its transaction.
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        // One row more than returned, so a full result can be told apart from a truncated one.
        this.jdbcTemplate.setMaxRows(MAX_ROWS + 1);
        this.jdbcTemplate.setQueryTimeout(TIMEOUT_SECONDS);
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
            description = "Run one read-only SQL SELECT on the PostgreSQL database and get the rows back as a table. "
                    + "The sample e-commerce tables are named t_* (users, products, orders, payments, shipments, comments). "
                    + "At most 200 rows are returned, and a query is stopped after 5 seconds.")
    public String databaseQuery(@ToolParam(description = "A PostgreSQL SELECT statement") String sql) {
        if (sql == null || sql.isBlank()) {
            return "The SQL must not be blank.";
        }
        if (!sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            return "Only SELECT statements are allowed.";
        }
        try {
            return readOnlyTransaction.execute(status -> {
                jdbcTemplate.execute("SET TRANSACTION READ ONLY");
                jdbcTemplate.execute("SET LOCAL statement_timeout = '" + TIMEOUT_SECONDS + "s'");
                return jdbcTemplate.query(sql, DatabaseTool::toTable);
            });
        } catch (DataAccessException e) {
            return failure(e);
        } catch (RuntimeException e) {
            log.warn("Database tool query failed", e);
            return "Query failed because of an internal error.";
        }
    }

    /** The database's own message tells the model what to fix; no stack trace or Java class names. */
    private static String failure(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof SQLException sqlException && QUERY_CANCELED.equals(sqlException.getSQLState())) {
            return "Query stopped: it ran longer than " + TIMEOUT_SECONDS + " seconds.";
        }
        return "Query failed: " + cause.getMessage();
    }

    /** A Markdown table, followed by the row count or a truncation note. */
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
                return out.append("(truncated to ").append(MAX_ROWS).append(" rows)").toString();
            }
            out.append('|');
            for (int i = 1; i <= columns; i++) {
                out.append(' ').append(cell(rs.getString(i))).append(" |");
            }
            out.append('\n');
            rows++;
        }
        return out.append('(').append(rows).append(rows == 1 ? " row)" : " rows)").toString();
    }

    /** Keeps one value in one table cell: NULL spelled out, pipes escaped, line breaks flattened. */
    private static String cell(String value) {
        if (value == null) {
            return "NULL";
        }
        return value.replace("|", "\\|").replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

}
