package com.study.grabthisforme.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class ConversationMemberId implements Serializable {
  public String conversationId;
  public Long userId;

  public ConversationMemberId() {}

  public ConversationMemberId(String conversationId, Long userId) {
    this.conversationId = conversationId;
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ConversationMemberId k
        && Objects.equals(conversationId, k.conversationId)
        && Objects.equals(userId, k.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(conversationId, userId);
  }
}
