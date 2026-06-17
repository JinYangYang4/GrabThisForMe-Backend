package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation")
public class ConversationEntity {

    @Id
    public String conversationId;
    public String conversationType;
    public Long targetId;
    public String lastMessageId;
    public Long lastTime;

    public ConversationEntity() {
    }
}
