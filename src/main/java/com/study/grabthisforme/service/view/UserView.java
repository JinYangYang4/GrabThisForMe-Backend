package com.study.grabthisforme.service.view;

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
    UserStatisticsView statistics
) {

    public record UserStatisticsView(
        Long likeCount,
        Long fanCount,
        Long followCount
    ) {
    }
}
