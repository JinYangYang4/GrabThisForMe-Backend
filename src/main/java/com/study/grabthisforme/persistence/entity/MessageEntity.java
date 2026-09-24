package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_content", indexes = {
    @jakarta.persistence.Index(name = "idx_message_retention", columnList = "timestamp,conversationId"),
    @jakarta.persistence.Index(name = "idx_message_page", columnList = "conversationId,timestamp,messageId")
})
public class MessageEntity {

    @Id
    public String messageId;
    public String clientMsgId;
    public String conversationId;
    public Long senderId;
    public String type;
    public String content;
    public String mediaUrl;
    public Long timestamp;
    public String status;
    public String replyToMessageId;
    public Long recalledAt;
    public String forwardSourceId;
    public String systemEvent;

    public MessageEntity() {
    }
}
