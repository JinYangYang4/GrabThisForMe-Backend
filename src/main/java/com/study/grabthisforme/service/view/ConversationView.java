package com.study.grabthisforme.service.view;
import java.util.List;
public record ConversationView(String conversationId,String conversationType,Long groupId,String groupName,Long createdAt,MessageView lastMessage,Long lastTime,Integer unreadCount,Boolean isHidden,Long lastReadTime,List<UserBriefView> participants,List<MemberView> memberships,Long pinnedAt,String groupRemark) {
 public record MemberView(Long userId,String role,Long joinedAt,Integer sortOrder,String nickname) {}
}
