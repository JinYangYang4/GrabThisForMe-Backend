package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ConversationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ConversationEntity c where c.conversationId = :id")
    Optional<ConversationEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") String id);

    List<ConversationEntity> findAllByOrderByLastTimeDesc();

    List<ConversationEntity> findAllByConversationIdIn(List<String> conversationIds);

}
