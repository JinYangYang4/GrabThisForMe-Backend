package com.study.grabthisforme.service.view;
public record FriendRequestView(String requestId,Long senderId,Long receiverId,String status,Long createdAt,Long handledAt,Long userId,UserBriefView user) {}
