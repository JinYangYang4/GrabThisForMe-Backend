package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import com.study.grabthisforme.persistence.entity.ConversationEntity;
import com.study.grabthisforme.persistence.entity.ConversationParticipantEntity;
import com.study.grabthisforme.persistence.entity.ConversationUserStateEntity;
import com.study.grabthisforme.persistence.entity.MessageEntity;
import com.study.grabthisforme.persistence.entity.UserGroupRelationEntity;
import com.study.grabthisforme.persistence.repository.ChatGroupRepository;
import com.study.grabthisforme.persistence.repository.ConversationParticipantRepository;
import com.study.grabthisforme.persistence.repository.ConversationRepository;
import com.study.grabthisforme.persistence.repository.ConversationUserStateRepository;
import com.study.grabthisforme.persistence.repository.MessageRepository;
import com.study.grabthisforme.persistence.repository.UserGroupRelationRepository;
import com.study.grabthisforme.service.view.ConversationView;
import com.study.grabthisforme.service.view.MessageView;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final ConversationUserStateRepository conversationUserStateRepository;
    private final MessageRepository messageRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final UserGroupRelationRepository userGroupRelationRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;
    private final PushService pushService;

    public ConversationService(
        ConversationRepository conversationRepository,
        ConversationParticipantRepository conversationParticipantRepository,
        ConversationUserStateRepository conversationUserStateRepository,
        MessageRepository messageRepository,
        ChatGroupRepository chatGroupRepository,
        UserGroupRelationRepository userGroupRelationRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler,
        PushService pushService
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationUserStateRepository = conversationUserStateRepository;
        this.messageRepository = messageRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.userGroupRelationRepository = userGroupRelationRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
        this.pushService = pushService;
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
        return conversations.stream()
            .map(entity -> viewAssembler.toConversationView(
                entity,
                userId,
                messages.get(entity.lastMessageId),
                participants.getOrDefault(entity.conversationId, List.of()),
                states.get(entity.conversationId)
            ))
            .filter(view -> !Boolean.TRUE.equals(view.isHidden()))
            .toList();
    }

    public List<MessageView> listMessages(long userId, String conversationId) {
        ensureParticipant(conversationId, userId);
        return messageRepository.findAllByConversationIdOrderByTimestampAsc(conversationId).stream()
            .map(viewAssembler::toMessageView)
            .toList();
    }

    @Transactional
    public ConversationView createSingleConversation(long userId, long peerUserId) {
        if (viewAssembler.getUserView(peerUserId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40401, "Peer user not found");
        }
        ConversationEntity existing = findExistingSingleConversation(userId, peerUserId);
        if (existing != null) {
            return buildConversationView(existing, userId);
        }

        ConversationEntity conversation = new ConversationEntity();
        conversation.conversationId = idGenerator.nextConversationId();
        conversation.conversationType = "SINGLE";
        conversation.targetId = peerUserId;
        conversation.lastMessageId = null;
        conversation.lastTime = System.currentTimeMillis();
        conversationRepository.save(conversation);

        saveParticipants(conversation.conversationId, List.of(userId, peerUserId));
        saveStateIfAbsent(conversation.conversationId, userId);
        saveStateIfAbsent(conversation.conversationId, peerUserId);
        return buildConversationView(conversation, userId);
    }

    @Transactional
    public ConversationView createGroupConversation(long userId, String groupName, List<Long> memberIds) {
        Set<Long> distinctMembers = new LinkedHashSet<>();
        distinctMembers.add(userId);
        if (memberIds != null) {
            distinctMembers.addAll(memberIds);
        }
        for (Long memberId : distinctMembers) {
            if (viewAssembler.getUserView(memberId) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, 40401, "Member user not found: " + memberId);
            }
        }

        long groupId = idGenerator.nextLongId();
        ChatGroupEntity group = new ChatGroupEntity();
        group.groupId = groupId;
        group.groupName = groupName;
        group.createTime = System.currentTimeMillis();
        chatGroupRepository.save(group);

        for (Long memberId : distinctMembers) {
            userGroupRelationRepository.save(new UserGroupRelationEntity(
                memberId,
                groupId,
                memberId.equals(userId) ? "OWNER" : "MEMBER",
                System.currentTimeMillis()
            ));
        }

        ConversationEntity conversation = new ConversationEntity();
        conversation.conversationId = idGenerator.nextConversationId();
        conversation.conversationType = "GROUP";
        conversation.targetId = groupId;
        conversation.lastMessageId = null;
        conversation.lastTime = System.currentTimeMillis();
        conversationRepository.save(conversation);
        saveParticipants(conversation.conversationId, new ArrayList<>(distinctMembers));
        distinctMembers.forEach(memberId -> saveStateIfAbsent(conversation.conversationId, memberId));
        return buildConversationView(conversation, userId);
    }

    @Transactional
    public MessageView sendMessage(long userId, String conversationId, String type, String content, String mediaUrl) {
        ensureParticipant(conversationId, userId);
        ConversationEntity conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40461, "Conversation not found"));
        MessageEntity message = new MessageEntity();
        message.messageId = idGenerator.nextMessageId();
        message.conversationId = conversationId;
        message.senderId = userId;
        message.type = type == null ? "TEXT" : type;
        message.content = content;
        message.mediaUrl = mediaUrl;
        message.timestamp = System.currentTimeMillis();
        message.status = "SENT";
        messageRepository.save(message);

        conversation.lastMessageId = message.messageId;
        conversation.lastTime = message.timestamp;
        conversationRepository.save(conversation);

        List<ConversationParticipantEntity> participants = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversationId);
        List<Long> participantIds = participants.stream().map(entity -> entity.userId).toList();
        for (Long participantId : participantIds) {
            ConversationUserStateEntity state = conversationUserStateRepository
                .findByConversationIdAndUserId(conversationId, participantId)
                .orElseGet(() -> new ConversationUserStateEntity(conversationId, participantId, 0, false));
            if (participantId.equals(userId)) {
                state.unreadCount = 0;
                state.isHidden = false;
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
        pushService.pushConversationMessage(conversationId, participantIds, payload);
        return messageView;
    }

    @Transactional
    public void markRead(long userId, String conversationId) {
        ensureParticipant(conversationId, userId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversationId, userId)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, userId, 0, false));
        state.unreadCount = 0;
        state.isHidden = false;
        conversationUserStateRepository.save(state);
    }

    @Transactional
    public void setHidden(long userId, String conversationId, boolean hidden) {
        ensureParticipant(conversationId, userId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversationId, userId)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, userId, 0, hidden));
        state.isHidden = hidden;
        conversationUserStateRepository.save(state);
    }

    private ConversationEntity findExistingSingleConversation(long userId, long peerUserId) {
        List<String> myConversationIds = conversationParticipantRepository.findAllByUserId(userId).stream()
            .map(entity -> entity.conversationId)
            .toList();
        for (String conversationId : myConversationIds) {
            ConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null || !"SINGLE".equals(conversation.conversationType)) {
                continue;
            }
            List<ConversationParticipantEntity> participants = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversationId);
            if (participants.size() == 2) {
                Set<Long> ids = participants.stream().map(entity -> entity.userId).collect(Collectors.toSet());
                if (ids.contains(userId) && ids.contains(peerUserId)) {
                    return conversation;
                }
            }
        }
        return null;
    }

    private void ensureParticipant(String conversationId, long userId) {
        boolean exists = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversationId).stream()
            .anyMatch(entity -> entity.userId.equals(userId));
        if (!exists) {
            throw new ApiException(HttpStatus.FORBIDDEN, 40361, "You are not in this conversation");
        }
    }

    private void saveParticipants(String conversationId, List<Long> userIds) {
        int index = 0;
        for (Long memberId : userIds) {
            conversationParticipantRepository.save(
                new ConversationParticipantEntity(conversationId, memberId, "", System.currentTimeMillis(), index++)
            );
        }
    }

    private void saveStateIfAbsent(String conversationId, long userId) {
        if (conversationUserStateRepository.findByConversationIdAndUserId(conversationId, userId).isEmpty()) {
            conversationUserStateRepository.save(new ConversationUserStateEntity(conversationId, userId, 0, false));
        }
    }

    private ConversationView buildConversationView(ConversationEntity conversation, long currentUserId) {
        List<ConversationParticipantEntity> participants = conversationParticipantRepository.findAllByConversationIdOrderBySortOrderAsc(conversation.conversationId);
        ConversationUserStateEntity state = conversationUserStateRepository.findByConversationIdAndUserId(conversation.conversationId, currentUserId)
            .orElse(null);
        MessageEntity lastMessage = conversation.lastMessageId == null ? null : messageRepository.findById(conversation.lastMessageId).orElse(null);
        return viewAssembler.toConversationView(conversation, currentUserId, lastMessage, participants, state);
    }
}
