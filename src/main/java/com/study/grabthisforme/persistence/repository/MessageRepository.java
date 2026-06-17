package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.MessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findAllByConversationIdOrderByTimestampAsc(String conversationId);

    List<MessageEntity> findAllByMessageIdIn(List<String> messageIds);

    Optional<MessageEntity> findTopByConversationIdOrderByTimestampDesc(String conversationId);
}
