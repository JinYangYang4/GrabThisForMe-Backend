package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;

@Entity
@Table(name = "conversation_participant", indexes = @Index(name="idx_conversation_participant_user", columnList="userId,conversationId"))
@IdClass(ConversationMemberId.class)
public class ConversationParticipantEntity {

    @Id public String conversationId;
    @Id public Long userId;
    public String role;
    public Long joinedAt;
    public Integer sortOrder;
    @jakarta.persistence.Column(length = 120)
    public String nickname;

    public ConversationParticipantEntity() {
    }

    public ConversationParticipantEntity(
        String conversationId,
        Long userId,
        String role,
        Long joinedAt,
        Integer sortOrder
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.sortOrder = sortOrder;
    }
}
