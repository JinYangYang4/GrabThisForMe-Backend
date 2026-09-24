package com.study.grabthisforme.service.view;

import java.util.List;

public record GroupView(
    Long groupId,
    String conversationId,
    String groupName,
    Long createTime,
    List<MemberView> members
) {

    public record MemberView(Long userId, String role, Long joinedTime, UserBriefView user, String nickname) {
    }
}
