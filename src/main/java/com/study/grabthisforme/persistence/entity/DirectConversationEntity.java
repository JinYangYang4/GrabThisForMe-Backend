package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "direct_conversation",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_direct_pair",
            columnNames = {"userLowId", "userHighId"}))
@org.hibernate.annotations.Check(constraints = "user_low_id < user_high_id")
public class DirectConversationEntity {
  @Id public String conversationId;

  @Column(nullable = false)
  public Long userLowId;

  @Column(nullable = false)
  public Long userHighId;

  public DirectConversationEntity() {}

  public DirectConversationEntity(String id, long a, long b) {
    conversationId = id;
    userLowId = Math.min(a, b);
    userHighId = Math.max(a, b);
  }
}
