package com.thinkxecai.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UuidTypeHandlerTest {

    private static final UUID ID = UUID.fromString("4522573b-2c34-425c-b333-e90b24c409ac");

    private final UuidTypeHandler handler = new UuidTypeHandler();

    @Test
    void bindsUuidAsNativeObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, ID, null);

        verify(ps).setObject(1, ID);
    }

    @Test
    void readsUuidObjectReturnedByTheDriver() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id")).thenReturn(ID);
        when(rs.getObject(1)).thenReturn(ID);

        assertThat(handler.getNullableResult(rs, "id")).isEqualTo(ID);
        assertThat(handler.getNullableResult(rs, 1)).isEqualTo(ID);
    }

    @Test
    void parsesUuidSelectedAsText() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(1)).thenReturn(ID.toString());

        assertThat(handler.getNullableResult(cs, 1)).isEqualTo(ID);
    }

    @Test
    void sqlNullReadsAsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("kb_id")).thenReturn(null);

        assertThat(handler.getNullableResult(rs, "kb_id")).isNull();
    }

    @Test
    void registeredHandlerIsFoundForUuidWithoutJdbcType() {
        // Mirrors how MyBatis resolves #{kbId} on an entity property: javaType UUID, jdbcType unknown.
        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        registry.register(UuidTypeHandler.class);

        assertThat(registry.getTypeHandler(UUID.class, (JdbcType) null)).isInstanceOf(UuidTypeHandler.class);
        assertThat(registry.getTypeHandler(UUID.class, JdbcType.OTHER)).isInstanceOf(UuidTypeHandler.class);
    }

}
