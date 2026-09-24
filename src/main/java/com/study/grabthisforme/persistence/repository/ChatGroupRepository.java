package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatGroupRepository extends JpaRepository<ChatGroupEntity, Long> {
    java.util.Optional<ChatGroupEntity> findByConversationId(String id);
    java.util.Optional<ChatGroupEntity> findByCreationKey(String key);
    List<ChatGroupEntity> findAllByConversationIdIn(List<String> ids);

    List<ChatGroupEntity> findAllByGroupNameContainingIgnoreCaseOrderByCreateTimeDesc(String keyword);
}
