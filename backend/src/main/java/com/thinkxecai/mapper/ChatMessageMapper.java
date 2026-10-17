package com.thinkxecai.mapper;

import com.thinkxecai.model.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * SQL lives in resources/mapper/ChatMessageMapper.xml. Message order relies on
 * chat_message.created_at DEFAULT clock_timestamp(), which keeps insert order even within one
 * transaction. Messages are never updated: history stays what the agent saw (ADR 031).
 */
public interface ChatMessageMapper {

    /** Fills in the database-generated id, createdAt, and updatedAt. {@code metadata} may be null. */
    int insert(ChatMessageEntity entity);

    ChatMessageEntity selectById(@Param("id") UUID id);

    /** All messages of a session, oldest first (the chat history view). */
    List<ChatMessageEntity> listBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * The latest {@code limit} messages of a session, returned oldest first (the agent's memory).
     * Not the first {@code limit} messages: a long session must still include the newest question.
     */
    List<ChatMessageEntity> selectRecentBySessionId(@Param("sessionId") UUID sessionId, @Param("limit") int limit);

    int deleteById(@Param("id") UUID id);

}
