package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;

@Entity
@Table(name = "user_friend_relation", indexes = @Index(name="idx_user_friend_relation_user", columnList="userId"))
@IdClass(FriendRelationId.class)
public class UserFriendRelationEntity {


    @Id public Long userId;
    @Id public Long friendUserId;
    public Long addedTime;
    @jakarta.persistence.Column(length = 120)
    public String remark;

    public UserFriendRelationEntity() {
    }

    public UserFriendRelationEntity(Long userId, Long friendUserId, Long addedTime) {
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.addedTime = addedTime;
    }
}
