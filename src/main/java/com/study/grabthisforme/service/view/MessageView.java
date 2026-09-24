package com.study.grabthisforme.service.view;

public record MessageView(
    String clientMsgId,
    String messageId,
    String conversationId,
    Long senderId,
    String type,
    String content,
    String mediaUrl,
    Long timestamp,
    String status,
    String replyToMessageId,
    String replyPreview,
    Long recalledAt,
    String systemEvent
) {
    public MessageView(String clientMsgId, String messageId, String conversationId, Long senderId,
        String type, String content, String mediaUrl, Long timestamp, String status) {
        this(clientMsgId, messageId, conversationId, senderId, type, content, mediaUrl, timestamp, status, null, null, null, null);
    }
}
