package com.thinkxecai.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Bridges pgvector's {@code vector} column type and a Java {@code float[]}, since MyBatis
 * has no built-in handler for it. Values travel as the pgvector text literal, e.g. "[0.1,0.2]".
 * The {@code postgresql} driver is runtime-scoped (see backend/pom.xml), so this handler binds
 * the literal as a plain string; mapper XML statements must cast the placeholder with
 * {@code ::vector}, the same convention used for similarity-search query vectors.
 */
@MappedTypes(float[].class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, toLiteral(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static float[] parse(String literal) {
        if (literal == null) {
            return null;
        }
        String trimmed = literal.trim();
        if (trimmed.length() < 2) {
            return new float[0];
        }
        String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",");
        if (parts.length == 1 && parts[0].isEmpty()) {
            return new float[0];
        }
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

}
