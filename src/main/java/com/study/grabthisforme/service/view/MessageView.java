package com.study.grabthisforme.service.view;

public record MessageView(
    String messageId,
    String conversationId,
    Long senderId,
    String type,
    String content,
    String mediaUrl,
    Long timestamp,
    String status
) {
}
