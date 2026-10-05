package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.DocumentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** SQL lives in resources/mapper/DocumentMapper.xml. */
public interface DocumentMapper {

    /** Fills in the database-generated id, createdAt, and updatedAt. {@code metadata} may be null. */
    int insert(DocumentEntity entity);

    DocumentEntity selectById(@Param("id") UUID id);

    /** Ordered by updated_at DESC. */
    List<DocumentEntity> listAll();

    /** Documents of one knowledge base, ordered by updated_at DESC. */
    List<DocumentEntity> listByKbId(@Param("kbId") UUID kbId);

    /** PATCH semantics: only non-null fields change; updated_at is always set to now(). */
    int update(DocumentEntity entity);

    /** The document's chunks are deleted with it (ON DELETE CASCADE). */
    int deleteById(@Param("id") UUID id);

}
