package com.thinkxecai.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Bridges PostgreSQL {@code uuid} columns and {@code java.util.UUID}. MyBatis 3.5 ships no UUID
 * handler: without this one, mapping a UUID property as a parameter fails at startup ("Type
 * handler was null on parameter mapping"), and UUID columns are silently skipped when reading,
 * so generated ids and foreign keys come back {@code null}.
 * <p>
 * The PostgreSQL driver binds and returns {@code java.util.UUID} natively through the standard
 * JDBC {@code setObject} / {@code getObject}, so no {@code ::uuid} casts are needed in mapper XML.
 * Text values (e.g. an id selected as {@code text}) are parsed as well.
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

}
