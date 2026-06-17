package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation_participant")
public class ConversationParticipantEntity {

    @Id
    public String id;
    public String conversationId;
    public Long userId;
    public String role;
    public Long joinedAt;
    public Integer sortOrder;

    public ConversationParticipantEntity() {
    }

    public ConversationParticipantEntity(
        String conversationId,
        Long userId,
        String role,
        Long joinedAt,
        Integer sortOrder
    ) {
        this.id = conversationId + ":" + userId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.sortOrder = sortOrder;
    }
}
