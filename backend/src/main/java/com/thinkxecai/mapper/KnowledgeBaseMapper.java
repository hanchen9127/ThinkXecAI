package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** SQL lives in resources/mapper/KnowledgeBaseMapper.xml. */
public interface KnowledgeBaseMapper {

    /** Fills in the database-generated id, createdAt, and updatedAt on {@code entity}. */
    int insert(KnowledgeBaseEntity entity);

    KnowledgeBaseEntity selectById(@Param("id") UUID id);

    /** Ordered by updated_at DESC. */
    List<KnowledgeBaseEntity> listAll();

    /** Unknown ids are ignored; an empty array returns an empty list. Bound as one uuid[] parameter. */
    List<KnowledgeBaseEntity> selectByIds(@Param("ids") UUID[] ids);

    /** PATCH semantics: only non-null fields change; updated_at is always set to now(). */
    int update(KnowledgeBaseEntity entity);

    int deleteById(@Param("id") UUID id);

}
