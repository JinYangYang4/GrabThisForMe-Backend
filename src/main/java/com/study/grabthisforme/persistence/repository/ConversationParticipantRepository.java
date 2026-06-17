package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ConversationParticipantEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipantEntity, String> {

    List<ConversationParticipantEntity> findAllByUserId(Long userId);

    List<ConversationParticipantEntity> findAllByConversationIdOrderBySortOrderAsc(String conversationId);

    List<ConversationParticipantEntity> findAllByConversationIdIn(List<String> conversationIds);

    long countByConversationId(String conversationId);

    void deleteAllByConversationId(String conversationId);
}
