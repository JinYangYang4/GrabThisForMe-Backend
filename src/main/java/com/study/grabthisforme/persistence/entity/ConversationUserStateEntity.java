package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation_user_state")
public class ConversationUserStateEntity {

    @Id
    public String id;
    public String conversationId;
    public Long userId;
    public Integer unreadCount;
    public Boolean isHidden;

    public ConversationUserStateEntity() {
    }

    public ConversationUserStateEntity(String conversationId, Long userId, Integer unreadCount, Boolean isHidden) {
        this.id = conversationId + ":" + userId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.unreadCount = unreadCount;
        this.isHidden = isHidden;
    }
}
