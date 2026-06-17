package com.study.grabthisforme.service.view;

import java.util.List;

public record ConversationView(
    String conversationId,
    String conversationType,
    Long targetId,
    MessageView lastMessage,
    Long lastTime,
    Integer unreadCount,
    Boolean isHidden,
    List<UserView> participants
) {
}
