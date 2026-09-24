package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ConversationParticipantEntity;
import java.util.List;
import com.study.grabthisforme.persistence.entity.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipantEntity, ConversationMemberId> {

    boolean existsByConversationIdAndUserId(String conversationId, Long userId);

    List<ConversationParticipantEntity> findAllByUserId(Long userId);

    List<ConversationParticipantEntity> findAllByConversationIdOrderBySortOrderAsc(String conversationId);

    List<ConversationParticipantEntity> findAllByConversationIdIn(List<String> conversationIds);

    long countByConversationId(String conversationId);

    void deleteAllByConversationId(String conversationId);
}
