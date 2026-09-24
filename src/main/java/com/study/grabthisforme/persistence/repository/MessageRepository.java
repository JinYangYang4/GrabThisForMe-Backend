package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.MessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    @Query("select m from MessageEntity m where m.conversationId = :cid and m.recalledAt is not null and " +
        "(m.recalledAt > :afterTime or (m.recalledAt = :afterTime and m.messageId > :afterId)) order by m.recalledAt, m.messageId")
    List<MessageEntity> recallUpdates(@Param("cid") String cid, @Param("afterTime") long afterTime,
        @Param("afterId") String afterId, org.springframework.data.domain.Pageable page);

    List<MessageEntity> findAllByConversationIdOrderByTimestampAsc(String conversationId);

    @Query("""
        SELECT m
        FROM MessageEntity m
        WHERE m.conversationId = :conversationId
          AND (:beforeTime IS NULL OR m.timestamp < :beforeTime OR (m.timestamp = :beforeTime AND :beforeId IS NOT NULL AND m.messageId < :beforeId))
        ORDER BY m.timestamp DESC, m.messageId DESC
        """)
    List<MessageEntity> findMessagePage(
        @Param("conversationId") String conversationId,
        @Param("beforeTime") Long beforeTime,
        @Param("beforeId") String beforeId,
        org.springframework.data.domain.Pageable pageable
    );

    List<MessageEntity> findAllByMessageIdIn(List<String> messageIds);

    Optional<MessageEntity> findByConversationIdAndSenderIdAndClientMsgId(
        String conversationId,
        Long senderId,
        String clientMsgId
    );

    Optional<MessageEntity> findTopByConversationIdOrderByTimestampDesc(String conversationId);
    @Query("""
        select distinct m.conversationId from MessageEntity m
        where m.timestamp < :cutoff and (:afterId is null or m.conversationId > :afterId)
        order by m.conversationId
        """)
    List<String> findExpiredConversationIds(@Param("cutoff") long cutoff, @Param("afterId") String afterId,
        org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from MessageEntity m where m.conversationId = :id and m.timestamp < :cutoff")
    int deleteExpired(@Param("id") String id, @Param("cutoff") long cutoff);

    @Query("""
        select count(m) from MessageEntity m where m.conversationId = :id
        and m.senderId <> :userId and (:readTime is null or m.timestamp > :readTime)
        """)
    long countUnreadRemaining(@Param("id") String id, @Param("userId") Long userId, @Param("readTime") Long readTime);

    // Includes legacy absolute URLs. False positives retain files rather than risk deleting them.
    @Query("select count(m) from MessageEntity m where m.mediaUrl like concat('%', :mediaId, '%')")
    long countMediaReferences(@Param("mediaId") String mediaId);
}
