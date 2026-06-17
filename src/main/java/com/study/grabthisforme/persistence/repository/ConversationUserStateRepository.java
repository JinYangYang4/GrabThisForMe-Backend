package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ConversationUserStateEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationUserStateRepository extends JpaRepository<ConversationUserStateEntity, String> {

    List<ConversationUserStateEntity> findAllByUserId(Long userId);

    List<ConversationUserStateEntity> findAllByConversationId(String conversationId);

    Optional<ConversationUserStateEntity> findByConversationIdAndUserId(String conversationId, Long userId);
}
