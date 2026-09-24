package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.MessageEntity;
import org.springframework.http.HttpStatus;

public final class MessageActionPolicy {
    public static final long RECALL_WINDOW_MS = 180_000L;
    private MessageActionPolicy() {}

    public static void requireRecallOwnerAndTime(MessageEntity message, long userId, long now) {
        if (message.senderId == null || message.senderId != userId)
            throw new ApiException(HttpStatus.FORBIDDEN, 40363, "只能撤回自己发送的消息");
        if (message.timestamp == null || now < message.timestamp || now - message.timestamp > RECALL_WINDOW_MS)
            throw new ApiException(HttpStatus.BAD_REQUEST, 40063, "消息超过 3 分钟，无法撤回");
        if ("SYSTEM".equals(message.type))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40064, "系统消息不能撤回");
    }
}
