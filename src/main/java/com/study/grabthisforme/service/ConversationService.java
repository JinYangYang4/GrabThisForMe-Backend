package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import com.study.grabthisforme.persistence.entity.ConversationEntity;
import com.study.grabthisforme.persistence.entity.ConversationParticipantEntity;
import com.study.grabthisforme.persistence.entity.ConversationUserStateEntity;
import com.study.grabthisforme.persistence.entity.MessageEntity;

import com.study.grabthisforme.persistence.repository.ChatGroupRepository;
import com.study.grabthisforme.persistence.repository.ConversationParticipantRepository;
import com.study.grabthisforme.persistence.repository.ConversationRepository;
import com.study.grabthisforme.persistence.repository.ConversationUserStateRepository;
import com.study.grabthisforme.persistence.repository.MessageRepository;

import com.study.grabthisforme.service.view.ConversationView;
import com.study.grabthisforme.service.view.ConversationPinView;
import com.study.grabthisforme.service.view.MessageView;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final ConversationUserStateRepository conversationUserStateRepository;
    private final MessageRepository messageRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final ConversationMembershipService membership;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;
    private final PushService pushService;
    private final MediaService mediaService;

    public ConversationService(
        ConversationRepository conversationRepository,
        ConversationParticipantRepository conversationParticipantRepository,
        ConversationUserStateRepository conversationUserStateRepository,
        MessageRepository messageRepository,
        ChatGroupRepository chatGroupRepository,
        ConversationMembershipService membership,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler,
        PushService pushService,
        MediaService mediaService
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationUserStateRepository = conversationUserStateRepository;
        this.messageRepository = messageRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.membership = membership;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
        this.pushService = pushService;
        this.mediaService = mediaService;
    }

    public List<ConversationView> listConversations(long userId) {
        List<String> conversationIds = conversationParticipantRepository.findAllByUserId(userId).stream()
            .map(entity -> entity.conversationId)
            .distinct()
            .toList();
        List<ConversationEntity> conversations = conversationRepository.findAllByConversationIdIn(conversationIds).stream()
            .sorted(Comparator.comparing(entity -> entity.lastTime, Comparator.reverseOrder()))
            .toList();
        Map<String, List<ConversationParticipantEntity>> participants = conversationParticipantRepository
            .findAllByConversationIdIn(conversationIds)
            .stream()
            .collect(Collectors.groupingBy(entity -> entity.conversationId));
        Map<String, ConversationUserStateEntity> states = conversationUserStateRepository.findAllByUserId(userId)
            .stream()
            .collect(Collectors.toMap(entity -> entity.conversationId, entity -> entity));
        Map<String, MessageEntity> messages = viewAssembler.loadMessagesByIds(
            conversations.stream().map(entity -> entity.lastMessageId).toList()
        );
        var users = viewAssembler.getUserBriefViews(participants.values().stream().flatMap(List::stream).map(p->p.userId).distinct().toList());
        var groups = chatGroupRepository.findAllByConversationIdIn(conversationIds).stream().collect(Collectors.toMap(g->g.conversationId,g->g));
        return conversations.stream()
            .map(entity -> viewAssembler.toConversationView(
                entity,
                userId,
                messages.get(entity.lastMessageId),
                participants.getOrDefault(entity.conversationId, List.of()),
                states.get(entity.conversationId), users, groups.get(entity.conversationId)
            ))
            .sorted(Comparator.comparing((ConversationView view) -> view.pinnedAt() != null).reversed()
                .thenComparing(ConversationView::lastTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ConversationView::pinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ConversationView::conversationId))
            .toList();
    }

    public List<MessageView> listMessages(long userId,String conversationId,Long beforeTime,int limit) {
        return listMessages(userId,conversationId,beforeTime,null,limit);
    }
    public List<MessageView> listMessages(long userId, String conversationId, Long beforeTime, String beforeId, int limit) {
        ensureParticipant(conversationId, userId);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<MessageEntity> entities = messageRepository.findMessagePage(
            conversationId,
            beforeTime, beforeId,
            PageRequest.of(0, safeLimit)
        );
        Collections.reverse(entities);
        return entities.stream()
            .map(viewAssembler::toMessageView)
            .toList();
    }

    @Transactional
    public ConversationView createSingleConversation(long userId,long peerUserId) {
        return buildConversationView(membership.direct(userId,peerUserId),userId);
    }
    @Transactional
    public ConversationView createGroupConversation(long userId,String groupName,List<Long> memberIds,String key) {
        var group=membership.createGroup(userId,groupName,memberIds,key);
        return buildConversationView(conversationRepository.findById(group.conversationId).orElseThrow(),userId);
    }
    @Transactional
    public ConversationView createGroupConversation(long userId,String groupName,List<Long> memberIds) {
        return createGroupConversation(userId,groupName,memberIds,java.util.UUID.randomUUID().toString());
    }
    public ConversationView openGroupConversation(long userId,long groupId) {
        var group=membership.group(groupId);
        membership.requireMember(group.conversationId,userId);
        return buildConversationView(conversationRepository.findById(group.conversationId).orElseThrow(),userId);
    }

    @Transactional
    public MessageView sendMessage(
        long userId, String conversationId, String clientMsgId, String type, String content, String mediaUrl
    ) {
        return sendMessage(userId, conversationId, clientMsgId, type, content, mediaUrl, null);
    }

    @Transactional
    public MessageView sendMessage(
        long userId,
        String conversationId,
        String clientMsgId,
        String type,
        String content,
        String mediaUrl,
        String replyToMessageId
    ) {
        ensureParticipant(conversationId, userId);
        ConversationEntity conversation = conversationRepository.findForUpdate(conversationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40461, "Conversation not found"));

        MessageEntity existingMessage = messageRepository
            .findByConversationIdAndSenderIdAndClientMsgId(conversationId, userId, clientMsgId)
            .orElse(null);
        if (existingMessage != null) {
            return viewAssembler.toMessageView(existingMessage);
        }

        if (clientMsgId == null || clientMsgId.isBlank() || clientMsgId.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, 40062, "无效的消息请求标识");
        if (replyToMessageId != null) {
            MessageEntity source = messageRepository.findById(replyToMessageId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, 40065, "引用消息已过期"));
            if (!conversationId.equals(source.conversationId) || source.recalledAt != null || "SYSTEM".equals(source.type))
                throw new ApiException(HttpStatus.BAD_REQUEST, 40065, "不能引用该消息，请取消引用后重试");
        }

        String messageType=type==null?"TEXT":type;
        if ("IMAGE".equals(messageType) || "VIDEO".equals(messageType)) {
            mediaUrl=mediaService.validateChatAttachment(userId,conversationId,mediaUrl,"VIDEO".equals(messageType));
            content=null;
        }
        else if (!"TEXT".equals(messageType) || content==null || content.isBlank() || content.length()>10000 || (mediaUrl!=null && !mediaUrl.isBlank()))
            throw new ApiException(HttpStatus.BAD_REQUEST,40062,"Invalid message content or type");

        MessageEntity message = new MessageEntity();
        message.messageId = idGenerator.nextMessageId();
        message.clientMsgId = clientMsgId;
        message.conversationId = conversationId;
        message.senderId = userId;
        message.type = type == null ? "TEXT" : type;
        message.content = content;
        message.mediaUrl = mediaUrl;
        message.timestamp = System.currentTimeMillis();
        message.status = "SENT";
        message.replyToMessageId = replyToMessageId;
        messageRepository.save(message);

        conversation.lastMessageId = message.messageId;
        conversation.lastTime = message.timestamp;
        conversationRepository.save(conversation);

        List<ConversationParticipantEntity> participants = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversationId);
        List<Long> participantIds = participants.stream().map(entity -> entity.userId).toList();
        for (Long participantId : participantIds) {
            ConversationUserStateEntity state = conversationUserStateRepository
                .findByConversationIdAndUserId(conversationId, participantId)
                .orElseGet(() -> new ConversationUserStateEntity(conversationId, participantId, 0, false, null));
            if (participantId.equals(userId)) {
                state.unreadCount = 0;
                state.isHidden = false;
                state.lastReadTime = message.timestamp;
            } else {
                state.unreadCount = (state.unreadCount == null ? 0 : state.unreadCount) + 1;
                state.isHidden = false;
            }
            conversationUserStateRepository.save(state);
        }

        MessageView messageView = viewAssembler.toMessageView(message);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "conversation.message");
        payload.put("conversationId", conversationId);
        payload.put("message", messageView);
        MessagePushAfterCommit.send(pushService, conversationId, participantIds, payload);
        return messageView;
    }

    @Transactional
    public void markRead(long userId, String conversationId, Long lastReadTime) {
        ensureParticipant(conversationId, userId);
        conversationRepository.findForUpdate(conversationId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversationId, userId)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, userId, 0, false, null));
        state.unreadCount = 0;
        state.lastReadTime = lastReadTime;
        conversationUserStateRepository.save(state);
    }

    @Transactional
    public void setHidden(long userId, String conversationId, boolean hidden) {
        conversationRepository.findForUpdate(conversationId);
        ensureParticipant(conversationId, userId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversationId, userId)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, userId, 0, hidden, null));
        state.isHidden = hidden;
        conversationUserStateRepository.save(state);
    }

    @Transactional
    public ConversationPinView setPinned(long userId, String conversationId, boolean pinned) {
        conversationRepository.findForUpdate(conversationId);
        ensureParticipant(conversationId, userId);
        ConversationUserStateEntity state = conversationUserStateRepository
            .findByConversationIdAndUserId(conversationId, userId)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, userId, 0, false, null));
        // Repeated enable requests retain the original tie-break time.
        state.pinnedAt = pinned ? (state.pinnedAt == null ? System.currentTimeMillis() : state.pinnedAt) : null;
        conversationUserStateRepository.save(state);
        return new ConversationPinView(conversationId, userId, state.pinnedAt);
    }

    private void ensureParticipant(String conversationId,long userId) {
        membership.requireMember(conversationId,userId);
    }

    private ConversationView buildConversationView(ConversationEntity conversation, long currentUserId) {
        List<ConversationParticipantEntity> participants = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversation.conversationId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversation.conversationId, currentUserId)
            .orElse(null);
        MessageEntity lastMessage = conversation.lastMessageId == null ? null : messageRepository.findById(conversation.lastMessageId).orElse(null);
        return viewAssembler.toConversationView(conversation, currentUserId, lastMessage, participants, state);
    }
}
