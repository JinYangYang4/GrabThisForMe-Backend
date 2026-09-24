package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;

@Entity
@Table(name = "conversation_user_state", indexes = @Index(name="idx_conversation_user_state_user", columnList="userId,conversationId"))
@IdClass(ConversationMemberId.class)
public class ConversationUserStateEntity {

    @Id public String conversationId;
    @Id public Long userId;
    public Integer unreadCount;
    public Boolean isHidden;
    public Long lastReadTime;
    @jakarta.persistence.Column(name = "pinned_at")
    public Long pinnedAt;
    @jakarta.persistence.Column(name = "group_remark", length = 120)
    public String groupRemark;

    public ConversationUserStateEntity() {
    }

    public ConversationUserStateEntity(
        String conversationId,
        Long userId,
        Integer unreadCount,
        Boolean isHidden,
        Long lastReadTime
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.unreadCount = unreadCount;
        this.isHidden = isHidden;
        this.lastReadTime = lastReadTime;
    }
}
