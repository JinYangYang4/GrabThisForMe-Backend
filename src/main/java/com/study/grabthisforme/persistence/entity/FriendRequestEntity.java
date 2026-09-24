package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "friend_request",
    indexes = {
      @Index(name = "idx_request_sender", columnList = "senderId,createdAt,requestId"),
      @Index(name = "idx_request_receiver", columnList = "receiverId,createdAt,requestId")
    })
public class FriendRequestEntity {
  @Id public String requestId;

  @Column(nullable = false)
  public Long senderId;

  @Column(nullable = false)
  public Long receiverId;

  @Column(nullable = false)
  public String status;

  @Column(nullable = false)
  public Long createdAt;

  public Long handledAt;

  @Column(unique = true)
  public String pendingPair;
}
