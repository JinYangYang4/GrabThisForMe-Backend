package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ConversationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findAllByOrderByLastTimeDesc();

    List<ConversationEntity> findAllByConversationIdIn(List<String> conversationIds);

    Optional<ConversationEntity> findByConversationTypeAndTargetId(String conversationType, Long targetId);
}
