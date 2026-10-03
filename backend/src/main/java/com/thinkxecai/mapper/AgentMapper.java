package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.AgentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * SQL lives in resources/mapper/AgentMapper.xml. The JSONB columns (allowed_tools, allowed_kbs,
 * chat_options) travel as raw JSON text; AgentConverter parses them.
 */
public interface AgentMapper {

    /**
     * Fills in the database-generated id, createdAt, and updatedAt on {@code entity}. A null JSONB
     * field is left out of the INSERT, so the column default applies ([] / default chat options).
     */
    int insert(AgentEntity entity);

    AgentEntity selectById(@Param("id") UUID id);

    /** Ordered by created_at DESC. */
    List<AgentEntity> listAll();

    /** PATCH semantics: only non-null fields change; updated_at is always set to now(). */
    int update(AgentEntity entity);

    /** Sessions of the deleted agent are kept with agent_id set to NULL (ON DELETE SET NULL). */
    int deleteById(@Param("id") UUID id);

}
