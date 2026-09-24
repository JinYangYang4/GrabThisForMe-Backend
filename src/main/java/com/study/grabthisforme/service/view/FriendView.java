package com.study.grabthisforme.service.view;

/** Only returned by the authenticated owner's friend directory. */
public record FriendView(Long id, String accountName, String name, String headPic, String remark) {
    public FriendView(UserBriefView user, String remark) {
        this(user.id(), user.accountName(), user.name(), user.headPic(), remark);
    }
}
