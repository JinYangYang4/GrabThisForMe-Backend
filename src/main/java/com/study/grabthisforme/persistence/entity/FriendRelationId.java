package com.study.grabthisforme.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class FriendRelationId implements Serializable {
  public Long userId;
  public Long friendUserId;

  public FriendRelationId() {}

  public FriendRelationId(Long userId, Long friendUserId) {
    this.userId = userId;
    this.friendUserId = friendUserId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof FriendRelationId k
        && Objects.equals(userId, k.userId)
        && Objects.equals(friendUserId, k.friendUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, friendUserId);
  }
}
