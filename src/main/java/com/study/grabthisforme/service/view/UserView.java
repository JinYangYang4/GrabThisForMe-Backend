package com.study.grabthisforme.service.view;

import java.util.List;

public record UserView(
    Long id,
    String accountName,
    String name,
    String headPic,
    String phone,
    String email,
    Integer gender,
    Boolean isVip,
    String signature,
    Long createTime,
    Long lastLoginTime,
    UserStatisticsView statistics,
    UserLikesView likes
) {

    public record UserStatisticsView(
        Long likeCount,
        Long fanCount,
        Long followCount,
        List<String> selfPosts
    ) {
    }

    public record UserLikesView(
        List<String> likedPostIds,
        List<Long> likedStoreIds,
        List<Long> likedGoodsIds
    ) {
    }
}
