package com.study.grabthisforme.service;

import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemMessageService {
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository members;
    private final ViewAssembler views;
    private final PushService push;
    @org.springframework.beans.factory.annotation.Autowired private ConversationUserStateRepository states;
    public SystemMessageService(MessageRepository messages, ConversationRepository conversations,
        ConversationParticipantRepository members, ViewAssembler views, PushService push) {
        this.messages = messages; this.conversations = conversations; this.members = members;
        this.views = views; this.push = push;
    }
    @Transactional
    public void publish(String cid, long actor, String event, String key, String text) {
        var conversation = conversations.findForUpdate(cid).orElseThrow();
        String clientKey = "SYS:" + cid + ":" + key;
        if (messages.findByConversationIdAndSenderIdAndClientMsgId(cid, actor, clientKey).isPresent()) return;
        var message = new MessageEntity();
        message.messageId = UUID.randomUUID().toString();
        message.clientMsgId = clientKey; message.conversationId = cid; message.senderId = actor;
        message.type = "SYSTEM"; message.systemEvent = event; message.content = text;
        message.timestamp = System.currentTimeMillis(); message.status = "SENT";
        messages.save(message);
        conversation.lastMessageId = message.messageId; conversation.lastTime = message.timestamp;
        conversations.save(conversation);
        for (var member : members.findAllByConversationIdOrderBySortOrderAsc(cid)) {
            var state = states.findByConversationIdAndUserId(cid, member.userId).orElse(null);
            if (state != null) {
                if (member.userId != actor) state.unreadCount = (state.unreadCount == null ? 0 : state.unreadCount) + 1;
                state.isHidden = false;
                states.save(state);
            }
        }
        var payload = Map.<String,Object>of("type", "conversation.message", "conversationId", cid, "message", views.toMessageView(message));
        MessagePushAfterCommit.send(push, cid, members.findAllByConversationIdOrderBySortOrderAsc(cid)
            .stream().map(m -> m.userId).toList(), payload);
    }
    public String name(long userId) {
        var user = views.getUserBriefView(userId);
        return user == null || user.name() == null || user.name().isBlank() ? "用户" + userId : user.name();
    }
}
