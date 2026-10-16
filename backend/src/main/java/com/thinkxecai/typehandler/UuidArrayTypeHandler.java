package com.thinkxecai.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Binds a {@code UUID[]} as one PostgreSQL {@code uuid[]} parameter, e.g. {@code id = ANY(#{ids})}, so a
 * lookup by many ids is one statement shape with one parameter instead of one parameter per id
 * (the driver refuses more than 65,535). MyBatis's own {@code ArrayTypeHandler} names a UUID array
 * {@code JAVA_OBJECT}, which the PostgreSQL driver cannot create.
 */
@MappedTypes(UUID[].class)
public class UuidArrayTypeHandler extends BaseTypeHandler<UUID[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID[] parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setArray(i, ps.getConnection().createArrayOf("uuid", parameter));
    }

    @Override
    public UUID[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuids(rs.getArray(columnName));
    }

    @Override
    public UUID[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuids(rs.getArray(columnIndex));
    }

    @Override
    public UUID[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuids(cs.getArray(columnIndex));
    }

    private static UUID[] toUuids(Array array) throws SQLException {
        return array == null ? null : (UUID[]) array.getArray();
    }

}
