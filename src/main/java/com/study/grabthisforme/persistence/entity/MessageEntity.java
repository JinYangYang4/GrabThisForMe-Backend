package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_content")
public class MessageEntity {

    @Id
    public String messageId;
    public String conversationId;
    public Long senderId;
    public String type;
    public String content;
    public String mediaUrl;
    public Long timestamp;
    public String status;

    public MessageEntity() {
    }
}
