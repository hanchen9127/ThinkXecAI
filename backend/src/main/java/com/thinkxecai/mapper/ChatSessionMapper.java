package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** SQL lives in resources/mapper/ChatSessionMapper.xml. */
public interface ChatSessionMapper {

    /** Fills in the database-generated id, createdAt, and updatedAt. {@code agentId} may be null. */
    int insert(ChatSessionEntity entity);

    ChatSessionEntity selectById(@Param("id") UUID id);

    /** Locks the row as the touch before a new message does: no delete, no agent change, one message at a time (ADR 037). */
    ChatSessionEntity selectByIdForNoKeyUpdate(@Param("id") UUID id);

    /** Ordered by updated_at DESC. */
    List<ChatSessionEntity> listAll();

    /** Sessions of one agent, ordered by updated_at DESC. */
    List<ChatSessionEntity> listByAgentId(@Param("agentId") UUID agentId);

    /** PATCH semantics: only non-null fields change; updated_at is always set to now(). */
    int update(ChatSessionEntity entity);

    /**
     * Sets updated_at = now() and nothing else. Called when a message is added, so session lists
     * (ordered by updated_at DESC) show the most recently active session first.
     */
    int touch(@Param("id") UUID id);

    /** The session's messages are deleted with it (ON DELETE CASCADE). */
    int deleteById(@Param("id") UUID id);

}
