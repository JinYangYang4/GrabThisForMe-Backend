package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.ConversationMemberId;
import com.study.grabthisforme.persistence.repository.ConversationParticipantRepository;
import com.study.grabthisforme.persistence.repository.ConversationRepository;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GroupNicknameService {
    private static final Logger log = LoggerFactory.getLogger(GroupNicknameService.class);
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository members;
    private final PushService push;

    public GroupNicknameService(ConversationRepository conversations,
            ConversationParticipantRepository members, PushService push) {
        this.conversations = conversations;
        this.members = members;
        this.push = push;
    }

    @Transactional
    public NicknameView setMyNickname(long owner, String conversationId, String text) {
        String nickname = normalize(text);
        var conversation = conversations.findForUpdate(conversationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40484, "Conversation not found"));
        var member = members.findById(new ConversationMemberId(conversationId, owner))
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, 40384, "Group membership required"));
        if (!"GROUP".equals(conversation.conversationType))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40084, "Group conversation required");
        if (!Objects.equals(member.nickname, nickname)) {
            member.nickname = nickname;
            members.save(member);
            var recipients = members.findAllByConversationIdOrderBySortOrderAsc(conversationId)
                .stream().map(m -> m.userId).distinct().toList();
            // Invalidate after commit, never broadcast private remarks or a stale name snapshot.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    for (Long userId : recipients) {
                        try {
                            push.pushToUser(userId, "conversation.members.updated",
                                Map.of("type", "conversation.members.updated", "conversationId", conversationId));
                        } catch (RuntimeException error) {
                            log.warn("Member refresh notification failed for conversation {}", conversationId, error);
                        }
                    }
                }
            });
        }
        return new NicknameView(conversationId, owner, nickname);
    }

    static String normalize(String text) {
        if (text == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, 40085, "nickname is required; use empty text to clear");
        String value = text.strip();
        if (value.codePointCount(0, value.length()) > 30 || value.codePoints().anyMatch(c ->
                Character.isISOControl(c) || c == 0x2028 || c == 0x2029))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40086, "Nickname must be a single line of at most 30 characters");
        return value.isEmpty() ? null : value;
    }

    public record NicknameView(String conversationId, long userId, String nickname) {}
}
