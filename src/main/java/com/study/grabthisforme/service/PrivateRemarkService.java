package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.ConversationUserStateEntity;
import com.study.grabthisforme.persistence.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateRemarkService {
    private final UserFriendRelationRepository friends;
    private final ConversationUserStateRepository states;
    private final ConversationRepository conversations;
    private final ConversationMembershipService membership;

    public PrivateRemarkService(UserFriendRelationRepository friends, ConversationUserStateRepository states,
            ConversationRepository conversations, ConversationMembershipService membership) {
        this.friends = friends;
        this.states = states;
        this.conversations = conversations;
        this.membership = membership;
    }

    @Transactional
    public RemarkView setFriendRemark(long owner, long peer, String text) {
        String remark = normalize(text);
        membership.lockUsers(owner, peer);
        var relation = friends.findByUserIdAndFriendUserId(owner, peer)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, 40381, "Friendship required"));
        relation.remark = remark;
        friends.save(relation);
        return new RemarkView(remark);
    }

    @Transactional
    public RemarkView setGroupRemark(long owner, String conversationId, String text) {
        String remark = normalize(text);
        var conversation = conversations.findForUpdate(conversationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40481, "Conversation not found"));
        membership.requireMember(conversationId, owner);
        if (!"GROUP".equals(conversation.conversationType))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40081, "Group conversation required");
        var state = states.findByConversationIdAndUserId(conversationId, owner)
            .orElseGet(() -> new ConversationUserStateEntity(conversationId, owner, 0, false, null));
        state.groupRemark = remark;
        states.save(state);
        return new RemarkView(remark);
    }

    static String normalize(String text) {
        if (text == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, 40082, "remark is required; use empty text to clear");
        String value = text.strip();
        if (value.codePointCount(0, value.length()) > 30 || value.codePoints().anyMatch(c ->
                Character.isISOControl(c) || c == 0x2028 || c == 0x2029))
            throw new ApiException(HttpStatus.BAD_REQUEST, 40083, "Remark must be a single line of at most 30 characters");
        return value.isEmpty() ? null : value;
    }

    public record RemarkView(String remark) {}
}
