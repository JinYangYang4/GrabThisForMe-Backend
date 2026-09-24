package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.MessageEntity;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.service.view.MessageView;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageActionsService {
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository members;
    private final ConversationMembershipService membership;
    private final ConversationService sender;
    private final MediaService media;
    private final ViewAssembler views;
    private final PushService push;
    public MessageActionsService(MessageRepository messages, ConversationRepository conversations,
        ConversationParticipantRepository members, ConversationMembershipService membership,
        ConversationService sender, MediaService media, ViewAssembler views, PushService push) {
        this.messages = messages; this.conversations = conversations; this.members = members;
        this.membership = membership; this.sender = sender; this.media = media; this.views = views; this.push = push;
    }
    private MessageEntity requireMessage(String cid, String id) {
        var m = messages.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40463, "消息不存在或已过期"));
        if (!cid.equals(m.conversationId)) throw new ApiException(HttpStatus.FORBIDDEN, 40363, "消息不属于当前会话");
        return m;
    }
    @Transactional
    public MessageView recall(long uid, String cid, String id) {
        membership.requireMember(cid, uid);
        conversations.findForUpdate(cid).orElseThrow();
        var m = requireMessage(cid, id);
        if (!Objects.equals(m.senderId, uid)) throw new ApiException(HttpStatus.FORBIDDEN, 40363, "只能撤回自己发送的消息");
        if (m.recalledAt != null) return views.toMessageView(m); // Retry an already successful request.
        MessageActionPolicy.requireRecallOwnerAndTime(m, uid, System.currentTimeMillis());
        m.recalledAt = System.currentTimeMillis(); m.type = "SYSTEM"; m.systemEvent = "MESSAGE_RECALLED";
        var user = views.getUserBriefView(uid);
        m.content = (user == null ? "用户" : user.name()) + "撤回了一条消息";
        m.mediaUrl = null; m.replyToMessageId = null;
        messages.save(m);
        var view = views.toMessageView(m);
        MessagePushAfterCommit.send(push, cid, members.findAllByConversationIdOrderBySortOrderAsc(cid)
            .stream().map(p -> p.userId).toList(), Map.of("type", "conversation.message", "conversationId", cid, "message", view));
        return view;
    }
    public List<MessageView> updates(long uid, String cid, long afterTime, String afterId) {
        membership.requireMember(cid, uid);
        return messages.recallUpdates(cid, Math.max(0, afterTime), afterId == null ? "" : afterId,
            PageRequest.of(0, 100)).stream().map(views::toMessageView).toList();
    }
    @Transactional
    public MessageView forward(long uid, String target, String sourceConversation, String sourceId, String clientId) {
        if (clientId == null || clientId.isBlank() || clientId.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, 40062, "无效的转发请求标识");
        membership.requireMember(sourceConversation, uid);
        membership.requireMember(target, uid);
        // Shared lock order prevents A->B and B->A concurrent forwards from deadlocking.
        java.util.stream.Stream.of(sourceConversation, target).distinct().sorted()
            .forEach(id -> conversations.findForUpdate(id).orElseThrow());
        var prior = messages.findByConversationIdAndSenderIdAndClientMsgId(target, uid, clientId).orElse(null);
        if (prior != null) {
            if (!Objects.equals(prior.forwardSourceId, sourceId))
                throw new ApiException(HttpStatus.CONFLICT, 40963, "转发标识已用于其他消息");
            return views.toMessageView(prior);
        }
        var source = requireMessage(sourceConversation, sourceId);
        if (source.recalledAt != null || !Set.of("TEXT", "IMAGE", "VIDEO").contains(source.type))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40066, "该消息无法转发");
        String attachment = source.mediaUrl == null ? null : media.copyForForward(uid, sourceConversation, target, source.mediaUrl);
        var result = sender.sendMessage(uid, target, clientId, source.type, source.content, attachment);
        var saved = messages.findById(result.messageId()).orElseThrow();
        saved.forwardSourceId = sourceId; messages.save(saved);
        return result;
    }
}
